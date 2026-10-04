package com.ninsky.cronos.domain.model.core;

import com.ninsky.cronos.domain.entity.enums.RecordStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * References {@link UnitType} by id only ({@code unitTypeId}), not as a nested domain object —
 * callers needing the unit type's own fields (e.g. its name for display) use the
 * {@link MeasurementUnitView} read model or fetch it via
 * {@link com.ninsky.cronos.domain.port.core.UnitTypeRepositoryPort}.
 * <p>
 * {@code multiplierToBase} is "how many base units one of this unit is" (kg = 1000 when the base is g).
 * The rules that keep it meaningful live in {@link com.ninsky.cronos.domain.service.core.MeasurementUnitRules}.
 */
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class MeasurementUnit {

    private Long id;
    private String codeIdentity;
    private String name;
    private String namePlural;
    private Long unitTypeId;
    private BigDecimal multiplierToBase;
    @Builder.Default
    private boolean isBaseUnit = false;
    @Builder.Default
    private boolean isSystemDefault = true;
    private Long userId; // null for system-owned units
    @Builder.Default
    private RecordStatus status = RecordStatus.ACTIVE;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;

    public boolean isActive() {
        return status == RecordStatus.ACTIVE;
    }

    /**
     * True when applying {@code candidate} would change how quantities in this unit convert —
     * the fields that, once a recipe or raw material references the unit, must stay frozen so
     * previously computed costs are never silently restated.
     */
    public boolean differsInConversionSemantics(Long candidateUnitTypeId, BigDecimal candidateMultiplier, boolean candidateIsBase) {
        return !Objects.equals(unitTypeId, candidateUnitTypeId)
                || multiplierToBase == null || candidateMultiplier == null
                || multiplierToBase.compareTo(candidateMultiplier) != 0
                || isBaseUnit != candidateIsBase;
    }
}
