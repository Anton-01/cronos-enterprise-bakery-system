package com.ninsky.cronos.domain.model.core;

import com.ninsky.cronos.domain.entity.enums.RecordStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * References {@link UnitType} by id only ({@code unitTypeId}), not as a nested domain object —
 * callers needing the unit type's own fields (e.g. its name for display) fetch it separately via
 * {@link com.ninsky.cronos.domain.port.core.UnitTypeRepositoryPort}.
 */
@Getter
@Setter
@Builder
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
}
