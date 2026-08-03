package com.ninsky.cronos.application.service.quote;

import com.ninsky.cronos.application.request.core.mail.EmailRequest;
import com.ninsky.cronos.application.request.quote.CreateQuoteRequest;
import com.ninsky.cronos.application.request.quote.QuoteItemRequest;
import com.ninsky.cronos.application.response.quote.*;
import com.ninsky.cronos.application.service.mail.MailService;
import com.ninsky.cronos.application.service.storage.CloudStorageService;
import com.ninsky.cronos.domain.entity.auth.User;
import com.ninsky.cronos.domain.entity.auth.UserProfile;
import com.ninsky.cronos.domain.entity.enums.QuoteStatus;
import com.ninsky.cronos.domain.entity.quote.Quote;
import com.ninsky.cronos.domain.entity.quote.QuoteAccessLog;
import com.ninsky.cronos.domain.entity.quote.QuoteItem;
import com.ninsky.cronos.domain.entity.recipes.Recipe;
import com.ninsky.cronos.domain.entity.recipes.RecipeFile;
import com.ninsky.cronos.infrastructure.exception.BusinessException;
import com.ninsky.cronos.infrastructure.exception.ResourceNotFoundException;
import com.ninsky.cronos.infrastructure.persistence.auth.UserRepository;
import com.ninsky.cronos.infrastructure.persistence.quote.QuoteAccessLogRepository;
import com.ninsky.cronos.infrastructure.persistence.quote.QuoteRepository;
import com.ninsky.cronos.infrastructure.persistence.recipe.RecipeRepository;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuoteService {

    private final QuoteRepository quoteRepository;
    private final RecipeRepository recipeRepository;
    private final UserRepository userRepository;
    private final CloudStorageService cloudStorageService;
    private final QuoteAccessLogRepository quoteAccessLogRepository;
    private final MailService mailService;

    @Value("${app.frontend.urlSharePublicQuote}")
    private String urlSharePublicQuote;

    @Transactional(readOnly = true)
    public Page<InternalQuoteResponse> getQuotesByUser(String username, Pageable pageable) {
        log.info("Fetching paginated quotes for user: {}", username);

        User user = userRepository.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        return quoteRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), pageable)
                .map(quote -> InternalQuoteResponse.builder().id(quote.getId())
                        .quoteNumber(quote.getQuoteNumber()).clientName(quote.getClientName())
                        .total(quote.getTotal()).status(quote.getStatus())
                        .createdAt(quote.getCreatedAt()).publicToken(quote.getPublicToken())
                        .build());
    }

    @Transactional
    public InternalQuoteResponse createQuote(String username, CreateQuoteRequest request) {
        log.info("Initiating quote creation for client: {}", request.clientName());

        User user = userRepository.findByUsername(username).orElseThrow();

        // Public token to share de link
        String generatedToken = UUID.randomUUID().toString().replace("-", "") +
                UUID.randomUUID().toString().replace("-", "").substring(0, 10);

        // Simple Format: CR-USERID-YYYYMMDDHHMM
        String quoteNumber = "CR-" + user.getId().toString().substring(0, 4).toUpperCase() + "-" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));

        Quote quote = Quote.builder().quoteNumber(quoteNumber).user(user).clientName(request.clientName())
                .clientEmail(request.clientEmail()).clientPhone(request.clientPhone())
                .clientAddress(request.clientAddress()).notes(request.notes())
                .status(QuoteStatus.DRAFT).validUntil(LocalDateTime.now().plusDays(request.validDays()))
                .currency(request.currency().toUpperCase()).publicToken(generatedToken)
                .taxRate(request.taxRate()).items(new ArrayList<>()).build();

        // (Server-Side Calculation)
        BigDecimal globalSubtotal = BigDecimal.ZERO;

        for (QuoteItemRequest itemReq : request.items()) {
            Recipe recipe = null;
            String imagePath = null;

            if (itemReq.recipeId() != null) {
                recipe = recipeRepository.findByIdAndUserId(itemReq.recipeId(), user.getId())
                        .orElseThrow(() -> new BusinessException("Recipe not found or access denied: " + itemReq.recipeId()));

                imagePath = recipe.getFiles().stream().filter(RecipeFile::isPrimary)
                        .findFirst().map(RecipeFile::getFilePath).orElse(null);
            }

            // Matemática del Item: Cantidad * Precio Unitario
            BigDecimal itemSubtotal = itemReq.quantity().multiply(itemReq.unitPrice()).setScale(2, RoundingMode.HALF_UP);

            QuoteItem item = QuoteItem.builder().recipe(recipe).productName(itemReq.productName())
                    .productDescription(itemReq.productDescription()).productSize(itemReq.productSize())
                    .imageFilePath(imagePath).quantity(itemReq.quantity())
                    .unitCost(itemReq.unitCost()).profitPercentage(itemReq.profitPercentage())
                    .unitPrice(itemReq.unitPrice()).subtotal(itemSubtotal)
                    .notes(itemReq.notes()).build();

            quote.addItem(item);
            globalSubtotal = globalSubtotal.add(itemSubtotal);
        }

        BigDecimal taxFactor = request.taxRate().divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
        BigDecimal taxAmount = globalSubtotal.multiply(taxFactor).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalAmount = globalSubtotal.add(taxAmount).setScale(2, RoundingMode.HALF_UP);

        quote.setSubtotal(globalSubtotal);
        quote.setTaxAmount(taxAmount);
        quote.setTotal(totalAmount);

        Quote savedQuote = quoteRepository.save(quote);
        log.info("Quote generated successfully. Quote ID: {}, Quote Number: {}", savedQuote.getId(), savedQuote.getQuoteNumber());

        return InternalQuoteResponse.builder().id(savedQuote.getId()).quoteNumber(savedQuote.getQuoteNumber())
                .clientName(savedQuote.getClientName()).total(savedQuote.getTotal())
                .status(savedQuote.getStatus()).createdAt(savedQuote.getCreatedAt())
                .publicToken(savedQuote.getPublicToken()).build();
    }

    @Transactional(readOnly = true)
    public InternalQuoteResponse getQuoteById(String username, UUID quoteId) {
        log.info("Fetching quote details for ID {} by user {}", quoteId, username);

        User user = userRepository.findByUsername(username).orElseThrow();
        Quote quote = quoteRepository.findByIdAndUserId(quoteId, user.getId()).orElseThrow(() -> new ResourceNotFoundException("Cotización no encontrada"));

        List<InternalQuoteItemResponse> itemDtos = quote.getItems().stream().map(item -> InternalQuoteItemResponse.builder()
                        .id(item.getId())
                        .recipeId(item.getRecipe() != null ? item.getRecipe().getId() : null)
                        .productName(item.getProductName())
                        .productDescription(item.getProductDescription())
                        .productSize(item.getProductSize())
                        .quantity(item.getQuantity())
                        .unitCost(item.getUnitCost())
                        .profitPercentage(item.getProfitPercentage())
                        .unitPrice(item.getUnitPrice())
                        .subtotal(item.getSubtotal())
                        .notes(item.getNotes())
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
                .items(itemDtos)
                .build();
    }

    @Transactional
    public InternalQuoteResponse updateQuote(String username, UUID quoteId, CreateQuoteRequest request) {
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
        quote.setTaxRate(request.taxRate());
        quote.setCurrency(request.currency().toUpperCase());
        quote.setValidUntil(LocalDateTime.now().plusDays(request.validDays()));
        quote.setDeliveryFee(request.deliveryFee());
        quote.setExtraFee(request.extraFee());
        quote.setExtraFeeDescription(request.extraFeeDescription());

        quote.getItems().clear();
        BigDecimal globalSubtotal = BigDecimal.ZERO;

        for (var itemReq : request.items()) {
            Recipe recipe = null;
            String imagePath = null;

            if (itemReq.recipeId() != null) {
                recipe = recipeRepository.findByIdAndUserId(itemReq.recipeId(), user.getId())
                        .orElseThrow(() -> new BusinessException("Recipe not found or access denied: " + itemReq.recipeId()));

                imagePath = recipe.getFiles().stream().filter(RecipeFile::isPrimary)
                        .findFirst().map(RecipeFile::getFilePath).orElse(null);
            }

            // Matemática del Item: Cantidad * Precio Unitario
            BigDecimal itemSubtotal = itemReq.quantity().multiply(itemReq.unitPrice()).setScale(2, RoundingMode.HALF_UP);

            QuoteItem item = QuoteItem.builder().recipe(recipe).productName(itemReq.productName())
                    .productDescription(itemReq.productDescription()).productSize(itemReq.productSize())
                    .imageFilePath(imagePath).quantity(itemReq.quantity())
                    .unitCost(itemReq.unitCost()).profitPercentage(itemReq.profitPercentage())
                    .unitPrice(itemReq.unitPrice()).subtotal(itemSubtotal)
                    .notes(itemReq.notes()).build();

            quote.addItem(item);
            globalSubtotal = globalSubtotal.add(itemSubtotal);
        }

        BigDecimal safeDeliveryFee = request.deliveryFee() != null ? request.deliveryFee() : BigDecimal.ZERO;
        BigDecimal safeExtraFee = request.extraFee() != null ? request.extraFee() : BigDecimal.ZERO;

        BigDecimal safeTaxRate = request.taxRate() != null ? request.taxRate() : BigDecimal.ZERO;
        BigDecimal taxFactor = safeTaxRate.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
        BigDecimal taxAmount = globalSubtotal.multiply(taxFactor).setScale(2, RoundingMode.HALF_UP);

        BigDecimal finalTotal = globalSubtotal.add(taxAmount).add(safeDeliveryFee).add(safeExtraFee).setScale(2, RoundingMode.HALF_UP);

        quote.setSubtotal(globalSubtotal);
        quote.setTaxAmount(taxAmount);
        quote.setTotal(finalTotal);

        quoteRepository.save(quote);
        log.info("Quote {} updated successfully. New total: {}", quote.getId(), quote.getTotal());

        return InternalQuoteResponse.builder().id(quote.getId()).quoteNumber(quote.getQuoteNumber())
                .clientName(quote.getClientName()).total(quote.getTotal())
                .status(quote.getStatus()).createdAt(quote.getCreatedAt())
                .publicToken(quote.getPublicToken()).build();
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

        boolean isExpired = quote.getValidUntil().isBefore(LocalDateTime.now());

        // Save analytics user access
        saveAccessLogQuoteAnalytics(quote, ipAddress, userAgent);

        quote.setViewsCount(quote.getViewsCount() + 1);
        quoteRepository.save(quote);

        log.info("Quote {} viewed successfully. Total views: {}", quote.getId(), quote.getViewsCount());

        List<PublicQuoteItemResponse> publicItems = extractPublicQuoteItems(quote);

        return PublicQuoteResponse.builder().quoteNumber(quote.getQuoteNumber()).bakerName(quote.getUser().getProfile().getBakerCompleteName())
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

        mailService.sendHtmlEmail(createEmailRequest(quote));

        log.info("Quote email sent successfully to {}", quote.getClientEmail());
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
                    .recipeId(item.getRecipe() != null ? item.getRecipe().getId() : null)
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

    private EmailRequest createEmailRequest(Quote quote) {
        UserProfile userProfile = quote.getUser().getProfile();

        return EmailRequest.builder().to(quote.getClientEmail()).subject(userProfile.getQuoteCompleteName())
                .templateName("quote/share-quote").variables(Map.of("completeName", userProfile.getCompleteName(),
                        "clientName", quote.getClientName(), "quoteNumber", quote.getQuoteNumber(),
                        "publicQuoteUrl", urlSharePublicQuote + quote.getPublicToken(),
                        "validUntil", quote.getValidUntil().toLocalDate().toString()))
                .build();
    }
    private void saveAccessLogQuoteAnalytics(Quote quote, String ipAddress, String userAgent) {
        QuoteAccessLog accessLog = QuoteAccessLog.builder().quote(quote).ipAddress(ipAddress).userAgent(userAgent).build();
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
                    .quantity(item.getQuantity()).unitPrice(item.getUnitPrice()).subtotal(item.getSubtotal()).build();
        }).toList();
    }
}
