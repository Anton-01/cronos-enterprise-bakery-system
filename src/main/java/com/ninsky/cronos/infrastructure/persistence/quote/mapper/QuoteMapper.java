package com.ninsky.cronos.infrastructure.persistence.quote.mapper;

import com.ninsky.cronos.domain.model.quote.Quote;
import com.ninsky.cronos.domain.model.quote.QuoteItem;
import com.ninsky.cronos.infrastructure.persistence.quote.entity.QuoteItemJpaEntity;
import com.ninsky.cronos.infrastructure.persistence.quote.entity.QuoteJpaEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class QuoteMapper {

    public Quote toDomain(QuoteJpaEntity entity) {
        if (entity == null) {
            return null;
        }
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
                .subtotal(entity.getSubtotal())
                .taxRate(entity.getTaxRate())
                .taxAmount(entity.getTaxAmount())
                .total(entity.getTotal())
                .currency(entity.getCurrency())
                .publicToken(entity.getPublicToken())
                .viewsCount(entity.getViewsCount())
                .isRevoked(entity.isRevoked())
                .version(entity.getVersion())
                .deliveryFee(entity.getDeliveryFee())
                .extraFee(entity.getExtraFee())
                .extraFeeDescription(entity.getExtraFeeDescription())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .items(entity.getItems().stream().map(this::toDomain).collect(Collectors.toList()))
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
                .publicToken(domain.getPublicToken())
                .viewsCount(domain.getViewsCount())
                .isRevoked(domain.isRevoked())
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

    private QuoteItem toDomain(QuoteItemJpaEntity entity) {
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
                .unitPrice(entity.getUnitPrice())
                .subtotal(entity.getSubtotal())
                .notes(entity.getNotes())
                .displayOrder(entity.getDisplayOrder())
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
                .version(domain.getVersion())
                .build();
    }
}
