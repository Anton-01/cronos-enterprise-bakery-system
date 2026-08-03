package com.ninsky.cronos.domain.model.core;

import com.ninsky.cronos.domain.entity.enums.RecordStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * References {@link Category}, {@link MeasurementUnit} (as {@code purchaseUnitId}), and
 * {@link Allergen} (as {@code allergenIds}) by id only — never as nested domain objects. Callers
 * needing data from those aggregates fetch it themselves via their own ports.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RawMaterial {

    private UUID id;
    private String name;
    private String description;
    private String brand;
    private String supplier;
    private Long categoryId;
    private UUID userId;
    private Long purchaseUnitId;
    private BigDecimal purchaseQuantity;
    private BigDecimal unitCost;
    @Builder.Default
    private String currency = "MXN";
    private BigDecimal yieldPercentage;
    private BigDecimal baseUnitCost;
    @Builder.Default
    private BigDecimal currentStock = BigDecimal.ZERO;
    private BigDecimal minimumStock;
    private LocalDateTime lastPurchaseDate;
    private LocalDateTime lastPriceUpdate;
    @Builder.Default
    private boolean needsRecalculation = false;
    @Builder.Default
    private RecordStatus status = RecordStatus.ACTIVE;
    @Builder.Default
    private BigDecimal density = BigDecimal.ONE;
    @Builder.Default
    private Set<UUID> allergenIds = new HashSet<>();

    public void addAllergen(UUID allergenId) {
        this.allergenIds.add(allergenId);
    }

    public void removeAllergen(UUID allergenId) {
        this.allergenIds.remove(allergenId);
    }
}
