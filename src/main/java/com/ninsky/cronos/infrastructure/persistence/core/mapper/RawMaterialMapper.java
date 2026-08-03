package com.ninsky.cronos.infrastructure.persistence.core.mapper;

import com.ninsky.cronos.domain.model.core.RawMaterial;
import com.ninsky.cronos.infrastructure.persistence.core.entity.AllergenJpaEntity;
import com.ninsky.cronos.infrastructure.persistence.core.entity.MeasurementUnitJpaEntity;
import com.ninsky.cronos.infrastructure.persistence.core.entity.RawMaterialJpaEntity;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class RawMaterialMapper {

    public RawMaterial toDomain(RawMaterialJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return RawMaterial.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .brand(entity.getBrand())
                .supplier(entity.getSupplier())
                .categoryId(entity.getCategoryId())
                .userId(entity.getUserId())
                .purchaseUnitId(entity.getPurchaseUnit() != null ? entity.getPurchaseUnit().getId() : null)
                .purchaseQuantity(entity.getPurchaseQuantity())
                .unitCost(entity.getUnitCost())
                .currency(entity.getCurrency())
                .yieldPercentage(entity.getYieldPercentage())
                .baseUnitCost(entity.getBaseUnitCost())
                .currentStock(entity.getCurrentStock())
                .minimumStock(entity.getMinimumStock())
                .lastPurchaseDate(entity.getLastPurchaseDate())
                .lastPriceUpdate(entity.getLastPriceUpdate())
                .needsRecalculation(entity.isNeedsRecalculation())
                .status(entity.getStatus())
                .density(entity.getDensity())
                .allergenIds(entity.getAllergens().stream().map(AllergenJpaEntity::getId).collect(Collectors.toSet()))
                .build();
    }

    /** Caller resolves {@code purchaseUnit}/{@code allergens} (via their own ports) before persisting. */
    public RawMaterialJpaEntity toEntity(RawMaterial domain, MeasurementUnitJpaEntity purchaseUnit, Set<AllergenJpaEntity> allergens) {
        if (domain == null) {
            return null;
        }
        return RawMaterialJpaEntity.builder()
                .id(domain.getId())
                .name(domain.getName())
                .description(domain.getDescription())
                .brand(domain.getBrand())
                .supplier(domain.getSupplier())
                .categoryId(domain.getCategoryId())
                .userId(domain.getUserId())
                .purchaseUnit(purchaseUnit)
                .purchaseQuantity(domain.getPurchaseQuantity())
                .unitCost(domain.getUnitCost())
                .currency(domain.getCurrency())
                .yieldPercentage(domain.getYieldPercentage())
                .baseUnitCost(domain.getBaseUnitCost())
                .currentStock(domain.getCurrentStock())
                .minimumStock(domain.getMinimumStock())
                .lastPurchaseDate(domain.getLastPurchaseDate())
                .lastPriceUpdate(domain.getLastPriceUpdate())
                .needsRecalculation(domain.isNeedsRecalculation())
                .status(domain.getStatus())
                .density(domain.getDensity())
                .allergens(allergens)
                .build();
    }
}
