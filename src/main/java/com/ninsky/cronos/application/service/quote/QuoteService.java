package com.ninsky.cronos.application.service.quote;

import com.ninsky.cronos.application.event.QuoteEmailRequestedEvent;
import com.ninsky.cronos.application.request.quote.CreateQuoteRequest;
import com.ninsky.cronos.application.request.quote.QuoteItemRequest;
import com.ninsky.cronos.application.response.envelope.ApiWarning;
import com.ninsky.cronos.application.response.quote.*;
import com.ninsky.cronos.infrastructure.storage.StoragePort;
import com.ninsky.cronos.domain.model.auth.User;
import com.ninsky.cronos.domain.model.auth.UserProfile;
import com.ninsky.cronos.domain.entity.enums.QuoteStatus;
import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.domain.model.audit.AuditSeverity;
import com.ninsky.cronos.domain.model.quote.Quote;
import com.ninsky.cronos.domain.model.quote.QuoteAccessLog;
import com.ninsky.cronos.domain.model.quote.QuoteItem;
import com.ninsky.cronos.domain.port.auth.UserProfileRepositoryPort;
import com.ninsky.cronos.domain.port.auth.UserRepositoryPort;
import com.ninsky.cronos.domain.port.quote.QuoteAccessLogRepositoryPort;
import com.ninsky.cronos.domain.port.quote.QuoteRepositoryPort;
import com.ninsky.cronos.finance.pricing.PriceLine;
import com.ninsky.cronos.finance.pricing.PricingCalculator;
import com.ninsky.cronos.finance.pricing.PricingResult;
import com.ninsky.cronos.finance.pricing.PricingSnapshot;
import com.ninsky.cronos.finance.settings.PricingSnapshotResolver;
import com.ninsky.cronos.iam.audit.AuditEvent;
import com.ninsky.cronos.iam.audit.AuditRecorder;
import com.ninsky.cronos.iam.audit.AuditTargets;
import com.ninsky.cronos.iam.permission.Permissions;
import com.ninsky.cronos.iam.shared.Actor;
import com.ninsky.cronos.iam.shared.ActorProvider;
import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.BusinessException;
import com.ninsky.cronos.infrastructure.exception.Violations;
import com.ninsky.cronos.kitchen.recipe.QuoteRecipePricer;
import com.ninsky.cronos.kitchen.shared.ApiWarningCode;
import com.ninsky.cronos.kitchen.shared.KitchenMessages;
import com.ninsky.cronos.kitchen.shared.Warned;
import com.ninsky.cronos.infrastructure.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuoteService {

    private final QuoteRepositoryPort quoteRepository;
    private final QuoteRecipePricer recipePricer;
    private final KitchenMessages messages;
    private final AuditRecorder audit;
    private final ActorProvider actors;
    private final Clock clock;
    private final UserRepositoryPort userRepository;
    private final UserProfileRepositoryPort userProfileRepository;
    private final StoragePort cloudStorageService;
    private final QuoteAccessLogRepositoryPort quoteAccessLogRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PricingSnapshotResolver pricingSnapshots;
    private final PricingCalculator pricingCalculator;

    @Transactional(readOnly = true)
    public Page<InternalQuoteResponse> getQuotesByUser(String username, Pageable pageable) {
        log.info("Fetching paginated quotes for user: {}", username);

        User user = userRepository.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        return quoteRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), pageable)
                .map(quote -> InternalQuoteResponse.builder().id(quote.getId())
                        .quoteNumber(quote.getQuoteNumber()).clientName(quote.getClientName())
                        .total(quote.getTotal()).status(quote.getStatus())
                        .createdAt(quote.getCreatedAt()).publicToken(quote.getPublicToken())
                        .priceReviewRequired(quote.isPriceReviewRequired())
                        .build());
    }

    @Transactional
    public Warned<InternalQuoteResponse> createQuote(String username, CreateQuoteRequest request) {
        log.info("Initiating quote creation for client: {}", request.clientName());

        User user = userRepository.findByUsername(username).orElseThrow();

        // Public token to share de link
        String generatedToken = UUID.randomUUID().toString().replace("-", "") +
                UUID.randomUUID().toString().replace("-", "").substring(0, 10);

        // Simple Format: CR-USERID-YYYYMMDDHHMM
        String quoteNumber = "CR-" + user.getId().toString().substring(0, 4).toUpperCase() + "-" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));

        PricingSnapshot snapshot = pricingSnapshots.forNewDocument(pricingInput(request));

        Quote quote = Quote.builder().quoteNumber(quoteNumber).userId(user.getId()).clientName(request.clientName())
                .clientEmail(request.clientEmail()).clientPhone(request.clientPhone())
                .clientAddress(request.clientAddress()).notes(request.notes())
                .status(QuoteStatus.DRAFT).validUntil(LocalDateTime.now().plusDays(request.validDays()))
                .publicToken(generatedToken).extraFeeDescription(request.extraFeeDescription())
                .items(new ArrayList<>()).build();

        Priced priced = price(quote, snapshot, request, actors.require());

        Quote savedQuote = quoteRepository.save(quote);
        auditBelowCost(savedQuote, request, priced.belowCostApproved());
        log.info("Quote generated successfully. Quote ID: {}, Quote Number: {}", savedQuote.getId(), savedQuote.getQuoteNumber());

        return new Warned<>(InternalQuoteResponse.builder().id(savedQuote.getId()).quoteNumber(savedQuote.getQuoteNumber())
                .clientName(savedQuote.getClientName()).total(savedQuote.getTotal())
                .status(savedQuote.getStatus()).createdAt(savedQuote.getCreatedAt())
                .publicToken(savedQuote.getPublicToken()).build(), priced.warnings());
    }

    @Transactional(readOnly = true)
    public InternalQuoteResponse getQuoteById(String username, UUID quoteId) {
        log.info("Fetching quote details for ID {} by user {}", quoteId, username);

        User user = userRepository.findByUsername(username).orElseThrow();
        Quote quote = quoteRepository.findByIdAndUserId(quoteId, user.getId()).orElseThrow(() -> new ResourceNotFoundException("Cotización no encontrada"));

        List<InternalQuoteItemResponse> itemDtos = quote.getItems().stream().map(item -> InternalQuoteItemResponse.builder()
                        .id(item.getId())
                        .recipeId(item.getRecipeId())
                        .productName(item.getProductName())
                        .productDescription(item.getProductDescription())
                        .productSize(item.getProductSize())
                        .quantity(item.getQuantity())
                        .unitCost(item.getUnitCost())
                        .profitPercentage(item.getProfitPercentage())
                        .unitPrice(item.getUnitPrice())
                        .subtotal(item.getSubtotal())
                        .notes(item.getNotes())
                        .recipeConfiguration(recipePricer.configuration(item.getRecipeConfiguration()))
                        .allergens(recipePricer.allergens(item.getAllergens()))
                        .recipeVersion(item.getRecipeVersion())
                        .costCalculatedAt(item.getCostCalculatedAt())
                        .priceReviewRequired(item.isPriceReviewRequired())
                        .build())
                .toList();

        // 2. Calculamos los días de vigencia dinámicamente
        int validDaysCalculated = (int) ChronoUnit.DAYS.between(
                quote.getCreatedAt().toLocalDate(),
                quote.getValidUntil().toLocalDate()
        );

        return InternalQuoteResponse.builder().id(quote.getId())
                .quoteNumber(quote.getQuoteNumber())
                .clientName(quote.getClientName()).clientEmail(quote.getClientEmail()).clientPhone(quote.getClientPhone())
                .clientAddress(quote.getClientAddress()).notes(quote.getNotes())
                .total(quote.getTotal()).taxRate(quote.getTaxRate()).currency(quote.getCurrency())
                .status(quote.getStatus()).validDays(validDaysCalculated)
                .createdAt(quote.getCreatedAt()).publicToken(quote.getPublicToken())
                .deliveryFee(quote.getDeliveryFee()).extraFee(quote.getExtraFee()).extraFeeDescription(quote.getExtraFeeDescription())
                .taxRateId(quote.getTaxRateId()).taxFactorType(quote.getTaxFactorType())
                .currencyDecimalPlaces(quote.getCurrencyDecimalPlaces()).pricesIncludeTax(quote.getPricesIncludeTax())
                .roundingMode(quote.getRoundingMode()).subtotal(quote.getSubtotal()).taxAmount(quote.getTaxAmount())
                .priceReviewRequired(quote.isPriceReviewRequired())
                .items(itemDtos)
                .build();
    }

    @Transactional
    public Warned<InternalQuoteResponse> updateQuote(String username, UUID quoteId, CreateQuoteRequest request) {
        log.info("Updating quote {} for user {}", quoteId, username);

        User user = userRepository.findByUsername(username).orElseThrow();
        Quote quote = quoteRepository.findByIdAndUserId(quoteId, user.getId()).orElseThrow(() -> new ResourceNotFoundException("Cotización no encontrada o sin acceso"));

        if (quote.getStatus() == QuoteStatus.ACCEPTED || quote.getStatus() == QuoteStatus.REJECTED) {
            log.warn("Attempt to update a finalized quote. Quote ID: {}, Status: {}", quoteId, quote.getStatus());
            throw new BusinessException("No puedes modificar una cotización que ya fue aceptada o rechazada.");
        }

        quote.setClientName(request.clientName());
        quote.setClientEmail(request.clientEmail());
        quote.setClientPhone(request.clientPhone());
        quote.setClientAddress(request.clientAddress());
        quote.setNotes(request.notes());
        quote.setValidUntil(LocalDateTime.now().plusDays(request.validDays()));
        quote.setExtraFeeDescription(request.extraFeeDescription());

        // Recalculate with the stored snapshot; only what the request changes is re-resolved
        PricingSnapshot snapshot = pricingSnapshots.forExistingDocument(snapshotOf(quote), pricingInput(request));
        quote.getItems().clear();
        Priced priced = price(quote, snapshot, request, actors.require());

        quoteRepository.save(quote);
        auditBelowCost(quote, request, priced.belowCostApproved());
        log.info("Quote {} updated successfully. New total: {}", quote.getId(), quote.getTotal());

        return new Warned<>(InternalQuoteResponse.builder().id(quote.getId()).quoteNumber(quote.getQuoteNumber())
                .clientName(quote.getClientName()).total(quote.getTotal())
                .status(quote.getStatus()).createdAt(quote.getCreatedAt())
                .publicToken(quote.getPublicToken()).build(), priced.warnings());
    }

    @Transactional
    public PublicQuoteResponse getPublicQuote(String token, String ipAddress, String userAgent) {
        log.info("Client attempting to view quote with token: {}", token);

        Quote quote = quoteRepository.findByPublicToken(token).orElseThrow(() -> {
            log.warn("Invalid quote token accessed: {}", token);
            return new ResourceNotFoundException("Cotización no encontrada o enlace inválido");
        });

        if (quote.isRevoked()) {
            log.warn("Attempt to access revoked quote token: {}", token);
            throw new BusinessException("Esta cotización ya no está disponible. Por favor, contacta a tu repostero.");
        }

        // Enforced the same way isRevoked already is — previously only computed as a flag and
        // returned in the response, never actually blocking access to an expired quote's data.
        if (quote.getValidUntil().isBefore(LocalDateTime.now())) {
            log.warn("Attempt to access expired quote token: {}", token);
            throw new BusinessException("Esta cotización ha expirado. Por favor, contacta a tu repostero.");
        }

        boolean isExpired = quote.getValidUntil().isBefore(LocalDateTime.now());

        // Save analytics user access
        saveAccessLogQuoteAnalytics(quote, ipAddress, userAgent);

        quote.setViewsCount(quote.getViewsCount() + 1);
        quoteRepository.save(quote);

        log.info("Quote {} viewed successfully. Total views: {}", quote.getId(), quote.getViewsCount());

        List<PublicQuoteItemResponse> publicItems = extractPublicQuoteItems(quote);

        UserProfile bakerProfile = userProfileRepository.findByUserId(quote.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Perfil de usuario no encontrado"));

        return PublicQuoteResponse.builder().quoteNumber(quote.getQuoteNumber()).bakerName(bakerProfile.getBakerCompleteName())
                .clientName(quote.getClientName()).notes(quote.getNotes()).quoteDate(quote.getCreatedAt().toLocalDate())
                .validUntil(quote.getValidUntil().toLocalDate()).subtotal(quote.getSubtotal()).taxRate(quote.getTaxRate())
                .taxAmount(quote.getTaxAmount()).total(quote.getTotal()).currency(quote.getCurrency()).status(quote.getStatus().name())
                .deliveryFee(quote.getDeliveryFee()).extraFee(quote.getExtraFee())
                .extraFeeDescription(quote.getExtraFeeDescription()).isExpired(isExpired).items(publicItems).build();
    }

    @Transactional
    public void revokeQuoteLink(String username, UUID quoteId) {
        log.info("Revoking public access for quote {} by user {}", quoteId, username);

        User user = userRepository.findByUsername(username).orElseThrow();
        Quote quote = quoteRepository.findByIdAndUserId(quoteId, user.getId()).orElseThrow(() -> new ResourceNotFoundException("Cotización no encontrada"));

        if (quote.isRevoked()) {
            throw new BusinessException("El enlace de esta cotización ya estaba revocado.");
        }

        quote.setRevoked(true);
        quote.setStatus(QuoteStatus.CANCELED);
        quote.setNotes("Cotización reovocada por el usuario propietario.");

        quoteRepository.save(quote);
        log.info("Public access revoked successfully for quote {}", quoteId);
    }

    @Transactional(readOnly = true)
    public void sendQuoteByEmail(String username, UUID quoteId) {
        log.info("Initiating email delivery for quote {} by user {}", quoteId, username);

        User user = userRepository.findByUsername(username).orElseThrow();
        Quote quote = quoteRepository.findByIdAndUserId(quoteId, user.getId()).orElseThrow(() -> new ResourceNotFoundException("Cotización no encontrada"));

        if (quote.getClientEmail() == null || quote.getClientEmail().isBlank()) {
            throw new BusinessException("El cliente no tiene un correo electrónico registrado en esta cotización.");
        }

        if (quote.isRevoked()) {
            throw new BusinessException("No puedes enviar por correo una cotización que ha sido revocada.");
        }

        eventPublisher.publishEvent(QuoteEmailRequestedEvent.builder().quoteId(quote.getId()).build());

        log.info("Quote email dispatch requested for {}", quote.getClientEmail());
    }

    @Transactional(readOnly = true)
    public BakerQuoteDetailResponse getQuoteDetailsForBaker(String username, UUID quoteId) {
        log.info("Fetching analytical quote details for ID {} by baker {}", quoteId, username);

        User user = userRepository.findByUsername(username).orElseThrow();
        Quote quote = quoteRepository.findByIdAndUserId(quoteId, user.getId()).orElseThrow(() -> new ResourceNotFoundException("Cotización no encontrada"));

        BigDecimal totalProductCost = BigDecimal.ZERO;
        List<InternalQuoteItemResponse> itemDtos = new ArrayList<>();

        for (var item : quote.getItems()) {
            BigDecimal itemTotalCost = item.getUnitCost().multiply(item.getQuantity());
            log.info("Item -> Name: {}. UnitCost: {}, Quantity: {}, UnitPrice: {}", item.getProductName(), item.getUnitCost(), item.getQuantity(), item.getUnitPrice());
            totalProductCost = totalProductCost.add(itemTotalCost);

            itemDtos.add(InternalQuoteItemResponse.builder().id(item.getId())
                    .recipeId(item.getRecipeId())
                    .productName(item.getProductName()).productSize(item.getProductSize())
                    .quantity(item.getQuantity()).unitCost(item.getUnitCost())
                    .unitPrice(item.getUnitPrice()).subtotal(item.getSubtotal()).build());
        }

        // 2. Traer el historial de accesos
        List<QuoteAccessLogResponse> logDtos = quoteAccessLogRepository.findByQuoteIdOrderByAccessedAtDesc(quote.getId())
                .stream().map(log -> {
                    String browser = "Unknown Device";
                    if (log.getUserAgent() != null) {
                        if (log.getUserAgent().contains("Mobile")) browser = "Mobile Device";
                        else if (log.getUserAgent().contains("Windows")) browser = "Windows PC";
                        else if (log.getUserAgent().contains("Mac")) browser = "Mac Computer";
                    }

                    return QuoteAccessLogResponse.builder().ipAddress(log.getIpAddress()).browserInfo(browser).accessedAt(log.getAccessedAt()).build();
                }).toList();

        totalProductCost = totalProductCost.setScale(2, RoundingMode.HALF_UP);

        // Ganancia Estimada = (Subtotal cobrado por los pasteles) - (Costo de hacer los pasteles)
        BigDecimal estimatedProfit = quote.getSubtotal().subtract(totalProductCost).setScale(2, RoundingMode.HALF_UP);

        return BakerQuoteDetailResponse.builder().id(quote.getId())
                .quoteNumber(quote.getQuoteNumber()).clientName(quote.getClientName()).clientEmail(quote.getClientEmail()).clientPhone(quote.getClientPhone())
                .status(quote.getStatus().name()).createdAt(quote.getCreatedAt()).validUntil(quote.getValidUntil()).subtotal(quote.getSubtotal())
                .taxAmount(quote.getTaxAmount()).deliveryFee(quote.getDeliveryFee()).extraFee(quote.getExtraFee()).totalRevenue(quote.getTotal())
                .totalProductCost(totalProductCost).estimatedProfit(estimatedProfit).viewsCount(quote.getViewsCount()).isRevoked(quote.isRevoked())
                .publicToken(quote.getPublicToken()).items(itemDtos).accessLogs(logDtos).build();
    }

    /** Warnings of the envelope and the item indexes approved below cost. */
    private record Priced(List<ApiWarning> warnings, List<Integer> belowCostApproved) {
    }

    /**
     * Applies the snapshot, re-prices recipe items with the cost engine (kitchen §6.2), enforces the
     * loss guard and prices every line through the single {@link PricingCalculator} (spec §11.2).
     */
    private Priced price(Quote quote, PricingSnapshot snapshot, CreateQuoteRequest request, Actor actor) {
        quote.setCurrency(snapshot.currencyCode());
        quote.setCurrencyDecimalPlaces(snapshot.currencyDecimalPlaces());
        quote.setTaxRateId(snapshot.taxRateId());
        quote.setTaxFactorType(snapshot.taxFactorType());
        quote.setTaxRate(snapshot.effectiveRatePercent());
        quote.setPricesIncludeTax(snapshot.pricesIncludeTax());
        quote.setRoundingMode(snapshot.roundingMode());
        // Saving re-prices every item, so the ripple's review flag is settled
        quote.setPriceReviewRequired(false);

        Instant now = clock.instant();
        List<Optional<QuoteRecipePricer.Priced>> recipes = IntStream.range(0, request.items().size())
                .mapToObj(i -> Optional.ofNullable(request.items().get(i).recipeId())
                        .map(id -> recipePricer.price(actor.id(), id, request.items().get(i).recipeConfiguration(), i, now)))
                .toList();
        Priced priced = guard(request, recipes, snapshot.currencyCode(), actor);

        PricingResult result = pricingCalculator.calculate(snapshot.rules(),
                request.items().stream().map(item -> new PriceLine(item.quantity(), item.unitPrice())).toList(),
                request.deliveryFee(), request.extraFee());
        IntStream.range(0, request.items().size())
                .mapToObj(i -> toItem(request.items().get(i), recipes.get(i).orElse(null), result.lines().get(i).net(), i))
                .forEach(quote::addItem);

        quote.setSubtotal(result.subtotal());
        quote.setTaxAmount(result.tax());
        quote.setDeliveryFee(result.deliveryFee());
        quote.setExtraFee(result.extraFee());
        quote.setTotal(result.total());
        return priced;
    }

    /** Loss guard and target-margin warnings (kitchen §6.2 rule 3); cost is comparable only in its own currency. */
    private Priced guard(CreateQuoteRequest request, List<Optional<QuoteRecipePricer.Priced>> recipes, String currency, Actor actor) {
        Violations violations = new Violations();
        List<ApiWarning> warnings = new ArrayList<>();
        List<Integer> approved = new ArrayList<>();
        boolean canApprove = actor.holds(Permissions.QUOTE_APPROVE);
        for (int i = 0; i < request.items().size(); i++) {
            QuoteItemRequest item = request.items().get(i);
            Optional<QuoteRecipePricer.Priced> recipe = recipes.get(i).filter(p -> p.currency().equalsIgnoreCase(currency));
            if (recipes.get(i).isPresent() && recipe.isEmpty()) {
                continue;
            }
            BigDecimal cost = recipe.map(QuoteRecipePricer.Priced::unitCost).orElse(item.unitCost());
            String field = "items[" + i + "].unitPrice";
            if (cost != null && item.unitPrice().compareTo(cost) < 0) {
                if (Boolean.TRUE.equals(item.allowBelowCost()) && canApprove) {
                    approved.add(i);
                } else {
                    violations.add(ApiErrorCode.PRICE_BELOW_COST, field, "kitchen.quote.priceBelowCost", cost.toPlainString());
                }
            } else if (recipe.isPresent() && item.unitPrice().compareTo(recipe.get().marginFloor()) < 0) {
                warnings.add(new ApiWarning(ApiWarningCode.BELOW_TARGET_MARGIN.name(), field,
                        messages.get("kitchen.warning.belowTargetMargin", recipe.get().marginFloor().toPlainString())));
            }
        }
        violations.throwIfAny();
        return new Priced(warnings, approved);
    }

    private void auditBelowCost(Quote quote, CreateQuoteRequest request, List<Integer> approved) {
        if (approved.isEmpty()) {
            return;
        }
        audit.record(AuditEvent.of(AuditAction.QUOTE_BELOW_COST_APPROVED, AuditTargets.QUOTE, quote.getId(), quote.getQuoteNumber())
                .severity(AuditSeverity.WARNING)
                .params(Map.of("items", approved.stream().map(i -> request.items().get(i).productName()).toList()))
                .build());
    }

    /** Recipe items keep the server cost and the snapshot; custom items keep what the client sent. */
    private QuoteItem toItem(QuoteItemRequest itemReq, QuoteRecipePricer.Priced recipe, BigDecimal lineNet, int order) {
        QuoteItem.QuoteItemBuilder item = QuoteItem.builder().productName(itemReq.productName())
                .productDescription(itemReq.productDescription()).productSize(itemReq.productSize())
                .quantity(itemReq.quantity()).unitCost(itemReq.unitCost()).profitPercentage(itemReq.profitPercentage())
                .unitPrice(itemReq.unitPrice()).subtotal(lineNet).notes(itemReq.notes()).displayOrder(order);
        if (recipe != null) {
            item.recipeId(recipe.recipeId()).unitCost(recipe.unitCost()).imageFilePath(recipe.coverKey())
                    .recipeConfiguration(recipe.configuration()).allergens(recipe.allergens())
                    .recipeVersion(recipe.recipeVersion()).costCalculatedAt(recipe.costCalculatedAt());
        }
        return item.build();
    }

    private static PricingSnapshotResolver.PricingInput pricingInput(CreateQuoteRequest request) {
        return new PricingSnapshotResolver.PricingInput(request.currency(), request.taxRate(), request.taxRateId());
    }

    static PricingSnapshot snapshotOf(Quote quote) {
        return new PricingSnapshot(quote.getCurrency(), quote.getCurrencyDecimalPlaces(), quote.getTaxRateId(), quote.getTaxFactorType(),
                quote.getTaxRate(), Boolean.TRUE.equals(quote.getPricesIncludeTax()), quote.getRoundingMode());
    }

    private void saveAccessLogQuoteAnalytics(Quote quote, String ipAddress, String userAgent) {
        QuoteAccessLog accessLog = QuoteAccessLog.builder().quoteId(quote.getId()).ipAddress(ipAddress).userAgent(userAgent).build();
        quoteAccessLogRepository.save(accessLog);
    }

    private List<PublicQuoteItemResponse> extractPublicQuoteItems(Quote quote) {
        return quote.getItems().stream().map(item -> {
            String signedImageUrl = null;
            if (item.getImageFilePath() != null) {
                signedImageUrl = cloudStorageService.generateSignedUrl(item.getImageFilePath(), 120);
            }

            return PublicQuoteItemResponse.builder().productName(item.getProductName())
                    .productDescription(item.getProductDescription()).productSize(item.getProductSize()).mainImageUrl(signedImageUrl)
                    .quantity(item.getQuantity()).unitPrice(item.getUnitPrice()).subtotal(item.getSubtotal())
                    .allergens(recipePricer.allergens(item.getAllergens())).build();
        }).toList();
    }
}
