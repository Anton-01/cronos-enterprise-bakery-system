package com.ninsky.cronos.application.service.quote;

import com.ninsky.cronos.application.request.quote.CreateQuoteRequest;
import com.ninsky.cronos.application.request.quote.QuoteItemRequest;
import com.ninsky.cronos.application.response.envelope.ApiWarning;
import com.ninsky.cronos.application.response.quote.*;
import com.ninsky.cronos.domain.entity.enums.QuoteStatus;
import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.domain.model.audit.AuditSeverity;
import com.ninsky.cronos.domain.model.auth.User;
import com.ninsky.cronos.domain.model.auth.UserProfile;
import com.ninsky.cronos.domain.model.quote.Quote;
import com.ninsky.cronos.domain.model.quote.QuoteAccessLog;
import com.ninsky.cronos.domain.model.quote.QuoteItem;
import com.ninsky.cronos.domain.port.auth.UserProfileRepositoryPort;
import com.ninsky.cronos.domain.port.auth.UserRepositoryPort;
import com.ninsky.cronos.domain.port.quote.QuoteAccessLogRepositoryPort;
import com.ninsky.cronos.domain.port.quote.QuoteRepositoryPort;
import com.ninsky.cronos.eventing.application.DomainEventPublisher;
import com.ninsky.cronos.eventing.application.TraceContexts;
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
import com.ninsky.cronos.iam.shared.TenantTime;
import com.ninsky.cronos.identity.infrastructure.AuthRateLimiter;
import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.infrastructure.exception.BusinessException;
import com.ninsky.cronos.infrastructure.exception.ResourceNotFoundException;
import com.ninsky.cronos.infrastructure.exception.Violations;
import com.ninsky.cronos.infrastructure.storage.StoragePort;
import com.ninsky.cronos.kitchen.recipe.QuoteRecipePricer;
import com.ninsky.cronos.kitchen.shared.ApiWarningCode;
import com.ninsky.cronos.kitchen.shared.KitchenMessages;
import com.ninsky.cronos.kitchen.shared.Warned;
import com.ninsky.cronos.quote.api.PublicAcceptRequest;
import com.ninsky.cronos.quote.api.PublicRejectRequest;
import com.ninsky.cronos.quote.application.QuoteLifecycleService;
import com.ninsky.cronos.quote.domain.QuoteActorType;
import com.ninsky.cronos.quote.domain.QuoteEvents;
import com.ninsky.cronos.quote.domain.QuoteNumbers;
import com.ninsky.cronos.quote.infrastructure.QuoteHistoryCustomRepository;
import com.ninsky.cronos.quote.infrastructure.QuoteQueryCustomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Quote documents: create/update (priced by the single pricing engine), reads for the baker (server-side paging and
 * search, detail with status history) and the public link (view, accept, reject). Status changes go through
 * {@link QuoteLifecycleService}; side effects (emails, notifications) are events handled after commit.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QuoteService {

    private static final int SEARCH_MIN = 2;

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
    private final PricingSnapshotResolver pricingSnapshots;
    private final PricingCalculator pricingCalculator;
    private final QuoteLifecycleService lifecycle;
    private final QuoteHistoryCustomRepository history;
    private final QuoteQueryCustomRepository queries;
    private final DomainEventPublisher events;
    private final AuthRateLimiter rateLimiter;

    /**
     * One page of the caller's quotes. {@code search} matches the number, client name, phone or email (accent- and
     * case-insensitive; client fields are encrypted at rest, so matching happens after decryption).
     *
     * @throws ApiException VALIDATION_ERROR on {@code sort}
     */
    @Transactional(readOnly = true)
    public Page<InternalQuoteResponse> page(String username, String search, List<QuoteStatus> statuses, int page, int size, String sort) {
        User user = userRepository.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        com.ninsky.cronos.infrastructure.web.paging.PageQuery query = com.ninsky.cronos.infrastructure.web.paging.PageQuery.of(
                page, size, sort, QuoteQueryCustomRepository.SORTS, "createdAt,desc");
        Set<QuoteStatus> filter = QuoteLifecycleService.statuses(statuses);
        String term = normalize(search);
        boolean byClientName = "clientName".equals(query.sortField());
        if ((term == null || term.length() < SEARCH_MIN) && !byClientName) {
            List<UUID> ids = queries.ids(user.getId(), filter, query.orderBySql(), query.size(), query.offset());
            return new PageImpl<>(load(ids).stream().map(this::toSummary).toList(), pageable(query), queries.count(user.getId(), filter));
        }
        List<Quote> candidates = load(queries.ids(user.getId(), filter, query.orderBySql(), null, 0));
        List<Quote> matching = candidates.stream().filter(quote -> term == null || term.length() < SEARCH_MIN || matches(quote, term)).toList();
        if (byClientName) {
            Comparator<Quote> byName = Comparator.comparing(quote -> Optional.ofNullable(normalize(quote.getClientName())).orElse(""));
            matching = matching.stream().sorted(query.ascending() ? byName : byName.reversed()).toList();
        }
        List<InternalQuoteResponse> content = matching.stream().skip(query.offset()).limit(query.size()).map(this::toSummary).toList();
        return new PageImpl<>(content, pageable(query), matching.size());
    }

    @Transactional
    public Warned<InternalQuoteResponse> createQuote(String username, CreateQuoteRequest request) {
        log.info("Initiating quote creation for client: {}", request.clientName());
        User user = userRepository.findByUsername(username).orElseThrow();
        if (request.eventDate() != null && request.eventDate().isBefore(TenantTime.today(clock))) {
            throw ApiException.invalid("eventDate", "quote.eventDate.past");
        }
        LocalDateTime now = TenantTime.nowLocal(clock);
        PricingSnapshot snapshot = pricingSnapshots.forNewDocument(pricingInput(request));

        Quote quote = Quote.builder().quoteNumber(QuoteNumbers.next(user.getId(), now)).userId(user.getId()).clientName(request.clientName())
                .clientEmail(request.clientEmail()).clientPhone(request.clientPhone())
                .clientAddress(request.clientAddress()).notes(request.notes())
                .status(QuoteStatus.DRAFT).validUntil(now.plusDays(request.validDays()))
                .publicToken(QuoteNumbers.publicToken()).extraFeeDescription(request.extraFeeDescription())
                .eventDate(request.eventDate())
                .items(new ArrayList<>()).build();

        Priced priced = price(quote, snapshot, request, actors.require());
        Quote savedQuote = quoteRepository.save(quote);
        history.append(savedQuote.getId(), QuoteStatus.DRAFT, QuoteActorType.BAKER, user.getId(), null, "CREATED", null,
                TenantTime.now(clock), MDC.get(TraceContexts.MDC_TRACE_ID));
        auditBelowCost(savedQuote, request, priced.belowCostApproved());
        events.publish(new QuoteEvents.QuoteCreated(savedQuote.getId(), savedQuote.getQuoteNumber(), savedQuote.getUserId()));
        log.info("Quote generated successfully. Quote ID: {}, Quote Number: {}", savedQuote.getId(), savedQuote.getQuoteNumber());
        return new Warned<>(toSummary(savedQuote), priced.warnings());
    }

    @Transactional(readOnly = true)
    public InternalQuoteResponse getQuoteById(String username, UUID quoteId) {
        Quote quote = owned(username, quoteId);
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

        return InternalQuoteResponse.builder().id(quote.getId())
                .quoteNumber(quote.getQuoteNumber())
                .clientName(quote.getClientName()).clientEmail(quote.getClientEmail()).clientPhone(quote.getClientPhone())
                .clientAddress(quote.getClientAddress()).notes(quote.getNotes())
                .total(quote.getTotal()).taxRate(quote.getTaxRate()).currency(quote.getCurrency())
                .status(quote.getStatus()).validDays(validDays(quote))
                .createdAt(quote.getCreatedAt()).publicToken(quote.getPublicToken())
                .deliveryFee(quote.getDeliveryFee()).extraFee(quote.getExtraFee()).extraFeeDescription(quote.getExtraFeeDescription())
                .taxRateId(quote.getTaxRateId()).taxFactorType(quote.getTaxFactorType())
                .currencyDecimalPlaces(quote.getCurrencyDecimalPlaces()).pricesIncludeTax(quote.getPricesIncludeTax())
                .roundingMode(quote.getRoundingMode()).subtotal(quote.getSubtotal()).taxAmount(quote.getTaxAmount())
                .priceReviewRequired(quote.isPriceReviewRequired())
                .eventDate(quote.getEventDate()).validUntil(quote.getValidUntil()).viewsCount(quote.getViewsCount())
                .calendarEventId(quote.getCalendarEventId())
                .items(itemDtos)
                .build();
    }

    /**
     * Edits a DRAFT, SENT, VIEWED or EXPIRED quote; editing an EXPIRED one recomputes {@code validUntil} and keeps it
     * EXPIRED until it is shared again.
     *
     * @throws ApiException INVALID_STATE_TRANSITION for a final quote
     */
    @Transactional
    public Warned<InternalQuoteResponse> updateQuote(String username, UUID quoteId, CreateQuoteRequest request) {
        log.info("Updating quote {} for user {}", quoteId, username);
        Quote quote = owned(username, quoteId);
        if (!quote.getStatus().editable()) {
            throw ApiException.withDetails(ApiErrorCode.INVALID_STATE_TRANSITION, "status",
                    Map.of("from", quote.getStatus().name(), "to", "UPDATED"), "quote.notEditable", quote.getStatus().name());
        }
        if (request.eventDate() != null && !request.eventDate().equals(quote.getEventDate())
                && request.eventDate().isBefore(TenantTime.today(clock))) {
            throw ApiException.invalid("eventDate", "quote.eventDate.past");
        }

        quote.setClientName(request.clientName());
        quote.setClientEmail(request.clientEmail());
        quote.setClientPhone(request.clientPhone());
        quote.setClientAddress(request.clientAddress());
        quote.setNotes(request.notes());
        quote.setValidUntil(TenantTime.nowLocal(clock).plusDays(request.validDays()));
        quote.setExtraFeeDescription(request.extraFeeDescription());
        quote.setEventDate(request.eventDate());

        // Recalculate with the stored snapshot; only what the request changes is re-resolved
        PricingSnapshot snapshot = pricingSnapshots.forExistingDocument(snapshotOf(quote), pricingInput(request));
        quote.getItems().clear();
        Priced priced = price(quote, snapshot, request, actors.require());

        Quote saved = quoteRepository.save(quote);
        auditBelowCost(saved, request, priced.belowCostApproved());
        events.publish(new QuoteEvents.QuoteUpdated(saved.getId(), saved.getQuoteNumber(), saved.getUserId()));
        log.info("Quote {} updated successfully. New total: {}", saved.getId(), saved.getTotal());
        return new Warned<>(toSummary(saved), priced.warnings());
    }

    /**
     * Public view; the first open of a SENT quote marks it VIEWED. Every open is logged.
     *
     * @throws ResourceNotFoundException for an unknown token; BusinessException for a revoked link
     */
    @Transactional
    public PublicQuoteResponse getPublicQuote(String token, String ipAddress, String userAgent) {
        rateLimiter.acquire(AuthRateLimiter.Limit.PUBLIC_QUOTE, token + "|" + ipAddress);
        Quote quote = byToken(token);
        saveAccessLogQuoteAnalytics(quote, ipAddress, userAgent);
        quote.setViewsCount(quote.getViewsCount() + 1);
        if (quote.getStatus() == QuoteStatus.SENT) {
            lifecycle.markViewed(quote);
        } else {
            quote = quoteRepository.save(quote);
            if (quote.getStatus() == QuoteStatus.VIEWED) {
                lifecycle.markViewed(quote);
            }
        }
        return toPublic(quoteRepository.findById(quote.getId()).orElseThrow());
    }

    /** The client accepts from the public link (rate-limited per token and IP). */
    @Transactional
    public PublicQuoteResponse acceptPublic(String token, String ipAddress, PublicAcceptRequest request) {
        rateLimiter.acquire(AuthRateLimiter.Limit.PUBLIC_QUOTE, token + "|" + ipAddress);
        return toPublic(lifecycle.acceptAsClient(byToken(token), request));
    }

    /** The client rejects from the public link (rate-limited per token and IP). */
    @Transactional
    public PublicQuoteResponse rejectPublic(String token, String ipAddress, PublicRejectRequest request) {
        rateLimiter.acquire(AuthRateLimiter.Limit.PUBLIC_QUOTE, token + "|" + ipAddress);
        return toPublic(lifecycle.rejectAsClient(byToken(token), request));
    }

    @Transactional(readOnly = true)
    public BakerQuoteDetailResponse getQuoteDetailsForBaker(String username, UUID quoteId) {
        Quote quote = owned(username, quoteId);
        BigDecimal totalProductCost = BigDecimal.ZERO;
        List<InternalQuoteItemResponse> itemDtos = new ArrayList<>();
        for (var item : quote.getItems()) {
            totalProductCost = totalProductCost.add(item.getUnitCost().multiply(item.getQuantity()));
            itemDtos.add(InternalQuoteItemResponse.builder().id(item.getId())
                    .recipeId(item.getRecipeId())
                    .productName(item.getProductName()).productSize(item.getProductSize())
                    .quantity(item.getQuantity()).unitCost(item.getUnitCost())
                    .unitPrice(item.getUnitPrice()).subtotal(item.getSubtotal()).build());
        }
        List<QuoteAccessLogResponse> logDtos = quoteAccessLogRepository.findByQuoteIdOrderByAccessedAtDesc(quote.getId())
                .stream().map(access -> QuoteAccessLogResponse.builder().ipAddress(access.getIpAddress())
                        .browserInfo(deviceOf(access.getUserAgent())).accessedAt(access.getAccessedAt()).build())
                .toList();
        totalProductCost = totalProductCost.setScale(2, RoundingMode.HALF_UP);
        // Estimated profit = what the client pays for the products − what making them costs
        BigDecimal estimatedProfit = quote.getSubtotal().subtract(totalProductCost).setScale(2, RoundingMode.HALF_UP);

        return BakerQuoteDetailResponse.builder().id(quote.getId())
                .quoteNumber(quote.getQuoteNumber()).clientName(quote.getClientName()).clientEmail(quote.getClientEmail())
                .clientPhone(quote.getClientPhone()).status(quote.getStatus().name()).createdAt(quote.getCreatedAt())
                .validUntil(quote.getValidUntil()).subtotal(quote.getSubtotal()).taxAmount(quote.getTaxAmount())
                .deliveryFee(quote.getDeliveryFee()).extraFee(quote.getExtraFee()).totalRevenue(quote.getTotal())
                .totalProductCost(totalProductCost).estimatedProfit(estimatedProfit).viewsCount(quote.getViewsCount())
                .isRevoked(quote.isRevoked()).publicToken(quote.getPublicToken()).items(itemDtos).accessLogs(logDtos)
                .currency(quote.getCurrency()).clientAddress(quote.getClientAddress()).notes(quote.getNotes())
                .eventDate(quote.getEventDate()).calendarEventId(quote.getCalendarEventId())
                .rejectReason(quote.getRejectReason() == null ? null : quote.getRejectReason().name())
                .statusHistory(history.history(quote.getId()))
                .build();
    }

    /** List row of a quote. */
    public InternalQuoteResponse toSummary(Quote quote) {
        return InternalQuoteResponse.builder().id(quote.getId()).quoteNumber(quote.getQuoteNumber())
                .clientName(quote.getClientName()).total(quote.getTotal()).currency(quote.getCurrency())
                .status(quote.getStatus()).createdAt(quote.getCreatedAt()).publicToken(quote.getPublicToken())
                .priceReviewRequired(quote.isPriceReviewRequired()).validDays(validDays(quote))
                .eventDate(quote.getEventDate()).validUntil(quote.getValidUntil()).viewsCount(quote.getViewsCount())
                .calendarEventId(quote.getCalendarEventId())
                .build();
    }

    private PublicQuoteResponse toPublic(Quote quote) {
        UserProfile bakerProfile = userProfileRepository.findByUserId(quote.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Perfil de usuario no encontrado"));
        return PublicQuoteResponse.builder().quoteNumber(quote.getQuoteNumber()).bakerName(bakerProfile.getBakerCompleteName())
                .clientName(quote.getClientName()).notes(quote.getNotes()).quoteDate(quote.getCreatedAt().toLocalDate())
                .validUntil(quote.getValidUntil().toLocalDate()).subtotal(quote.getSubtotal()).taxRate(quote.getTaxRate())
                .taxAmount(quote.getTaxAmount()).total(quote.getTotal()).currency(quote.getCurrency()).status(quote.getStatus().name())
                .deliveryFee(quote.getDeliveryFee()).extraFee(quote.getExtraFee())
                .extraFeeDescription(quote.getExtraFeeDescription()).isExpired(lifecycle.expired(quote))
                .canRespond(lifecycle.canRespond(quote)).eventDate(quote.getEventDate())
                .items(extractPublicQuoteItems(quote)).build();
    }

    private Quote byToken(String token) {
        Quote quote = quoteRepository.findByPublicToken(token).filter(found -> found.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Cotización no encontrada o enlace inválido"));
        if (quote.isRevoked() || quote.getStatus() == QuoteStatus.REVOKED) {
            throw new BusinessException("Esta cotización ya no está disponible. Por favor, contacta a tu repostero.");
        }
        return quote;
    }

    private Quote owned(String username, UUID quoteId) {
        User user = userRepository.findByUsername(username).orElseThrow();
        return quoteRepository.findByIdAndUserId(quoteId, user.getId()).filter(quote -> quote.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Cotización no encontrada"));
    }

    private List<Quote> load(List<UUID> ids) {
        Map<UUID, Quote> byId = quoteRepository.findAllById(ids).stream().collect(Collectors.toMap(Quote::getId, Function.identity()));
        return ids.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
    }

    private static boolean matches(Quote quote, String term) {
        String digits = term.replaceAll("\\D", "");
        return contains(quote.getQuoteNumber(), term) || contains(quote.getClientName(), term) || contains(quote.getClientEmail(), term)
                || (digits.length() >= 3 && quote.getClientPhone() != null && quote.getClientPhone().replaceAll("\\D", "").contains(digits));
    }

    private static boolean contains(String value, String term) {
        String normalized = normalize(value);
        return normalized != null && normalized.contains(term);
    }

    /** Lower case without accents (ñ is kept). */
    static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String decomposed = Normalizer.normalize(value.trim().toLowerCase(Locale.ROOT).replace("ñ", "\uE000"), Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "").replace("\uE000", "ñ");
    }

    private static org.springframework.data.domain.Pageable pageable(com.ninsky.cronos.infrastructure.web.paging.PageQuery query) {
        return PageRequest.of(query.page(), query.size(),
                Sort.by(query.ascending() ? Sort.Direction.ASC : Sort.Direction.DESC, query.sortField()));
    }

    private static int validDays(Quote quote) {
        return quote.getCreatedAt() == null || quote.getValidUntil() == null ? 0
                : (int) ChronoUnit.DAYS.between(quote.getCreatedAt().toLocalDate(), quote.getValidUntil().toLocalDate());
    }

    private static String deviceOf(String userAgent) {
        if (userAgent == null) {
            return "Unknown Device";
        }
        if (userAgent.contains("Mobile")) {
            return "Mobile Device";
        }
        if (userAgent.contains("Windows")) {
            return "Windows PC";
        }
        return userAgent.contains("Mac") ? "Mac Computer" : "Unknown Device";
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
