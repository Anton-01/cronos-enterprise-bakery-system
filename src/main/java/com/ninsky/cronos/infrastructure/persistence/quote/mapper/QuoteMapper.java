package com.ninsky.cronos.infrastructure.persistence.quote.mapper;

import com.ninsky.cronos.domain.model.quote.Quote;
import com.ninsky.cronos.domain.model.quote.QuoteItem;
import com.ninsky.cronos.infrastructure.persistence.quote.entity.QuoteItemJpaEntity;
import com.ninsky.cronos.infrastructure.persistence.quote.entity.QuoteJpaEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class QuoteMapper {

    public Quote toDomain(QuoteJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        Integer scale = entity.getCurrencyDecimalPlaces() == null ? null : entity.getCurrencyDecimalPlaces().intValue();
        return Quote.builder()
                .id(entity.getId())
                .quoteNumber(entity.getQuoteNumber())
                .userId(entity.getUserId())
                .clientName(entity.getClientName())
                .clientEmail(entity.getClientEmail())
                .clientPhone(entity.getClientPhone())
                .clientAddress(entity.getClientAddress())
                .notes(entity.getNotes())
                .status(entity.getStatus())
                .validUntil(entity.getValidUntil())
                .subtotal(money(entity.getSubtotal(), scale))
                .taxRate(entity.getTaxRate())
                .taxAmount(money(entity.getTaxAmount(), scale))
                .total(money(entity.getTotal(), scale))
                .currency(entity.getCurrency())
                .currencyDecimalPlaces(scale)
                .taxRateId(entity.getTaxRateId())
                .taxFactorType(entity.getTaxFactorType())
                .pricesIncludeTax(entity.getPricesIncludeTax())
                .roundingMode(entity.getRoundingMode())
                .publicToken(entity.getPublicToken())
                .viewsCount(entity.getViewsCount())
                .isRevoked(entity.isRevoked())
                .priceReviewRequired(entity.isPriceReviewRequired())
                .version(entity.getVersion())
                .deliveryFee(money(entity.getDeliveryFee(), scale))
                .extraFee(money(entity.getExtraFee(), scale))
                .extraFeeDescription(entity.getExtraFeeDescription())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .items(entity.getItems().stream().map(item -> toDomain(item, scale)).collect(Collectors.toList()))
                .build();
    }

    public QuoteJpaEntity toEntity(Quote domain) {
        if (domain == null) {
            return null;
        }
        QuoteJpaEntity entity = QuoteJpaEntity.builder()
                .id(domain.getId())
                .quoteNumber(domain.getQuoteNumber())
                .userId(domain.getUserId())
                .clientName(domain.getClientName())
                .clientEmail(domain.getClientEmail())
                .clientPhone(domain.getClientPhone())
                .clientAddress(domain.getClientAddress())
                .notes(domain.getNotes())
                .status(domain.getStatus())
                .validUntil(domain.getValidUntil())
                .subtotal(domain.getSubtotal())
                .taxRate(domain.getTaxRate())
                .taxAmount(domain.getTaxAmount())
                .total(domain.getTotal())
                .currency(domain.getCurrency())
                .currencyDecimalPlaces(domain.getCurrencyDecimalPlaces() == null ? null : domain.getCurrencyDecimalPlaces().shortValue())
                .taxRateId(domain.getTaxRateId())
                .taxFactorType(domain.getTaxFactorType())
                .pricesIncludeTax(domain.getPricesIncludeTax())
                .roundingMode(domain.getRoundingMode())
                .publicToken(domain.getPublicToken())
                .viewsCount(domain.getViewsCount())
                .isRevoked(domain.isRevoked())
                .priceReviewRequired(domain.isPriceReviewRequired())
                .version(domain.getVersion())
                .deliveryFee(domain.getDeliveryFee())
                .extraFee(domain.getExtraFee())
                .extraFeeDescription(domain.getExtraFeeDescription())
                .build();
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());

        List<QuoteItemJpaEntity> itemEntities = domain.getItems().stream()
                .map(i -> toEntity(i, entity)).collect(Collectors.toList());
        entity.setItems(itemEntities);
        return entity;
    }

    /**
     * Amounts are stored at scale 4 (V13); expose them at the snapshot currency's scale whenever
     * that is lossless, so an MXN total still reads 100.00.
     */
    static BigDecimal money(BigDecimal value, Integer decimalPlaces) {
        if (value == null || decimalPlaces == null) {
            return value;
        }
        return value.setScale(Math.max(decimalPlaces, value.stripTrailingZeros().scale()), RoundingMode.UNNECESSARY);
    }

    private QuoteItem toDomain(QuoteItemJpaEntity entity, Integer scale) {
        return QuoteItem.builder()
                .id(entity.getId())
                .recipeId(entity.getRecipeId())
                .productName(entity.getProductName())
                .productDescription(entity.getProductDescription())
                .productSize(entity.getProductSize())
                .imageFilePath(entity.getImageFilePath())
                .quantity(entity.getQuantity())
                .scaleFactor(entity.getScaleFactor())
                .unitCost(entity.getUnitCost())
                .profitPercentage(entity.getProfitPercentage())
                .unitPrice(money(entity.getUnitPrice(), scale))
                .subtotal(money(entity.getSubtotal(), scale))
                .notes(entity.getNotes())
                .displayOrder(entity.getDisplayOrder())
                .recipeConfiguration(entity.getRecipeConfiguration())
                .allergens(entity.getAllergens())
                .recipeVersion(entity.getRecipeVersion())
                .costCalculatedAt(entity.getCostCalculatedAt())
                .priceReviewRequired(entity.isPriceReviewRequired())
                .version(entity.getVersion())
                .build();
    }

    private QuoteItemJpaEntity toEntity(QuoteItem domain, QuoteJpaEntity parent) {
        return QuoteItemJpaEntity.builder()
                .id(domain.getId())
                .quote(parent)
                .recipeId(domain.getRecipeId())
                .productName(domain.getProductName())
                .productDescription(domain.getProductDescription())
                .productSize(domain.getProductSize())
                .imageFilePath(domain.getImageFilePath())
                .quantity(domain.getQuantity())
                .scaleFactor(domain.getScaleFactor())
                .unitCost(domain.getUnitCost())
                .profitPercentage(domain.getProfitPercentage())
                .unitPrice(domain.getUnitPrice())
                .subtotal(domain.getSubtotal())
                .notes(domain.getNotes())
                .displayOrder(domain.getDisplayOrder())
                .recipeConfiguration(domain.getRecipeConfiguration())
                .allergens(domain.getAllergens() == null ? "[]" : domain.getAllergens())
                .recipeVersion(domain.getRecipeVersion())
                .costCalculatedAt(domain.getCostCalculatedAt())
                .priceReviewRequired(domain.isPriceReviewRequired())
                .version(domain.getVersion())
                .build();
    }
}
