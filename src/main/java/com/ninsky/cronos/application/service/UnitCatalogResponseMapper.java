package com.ninsky.cronos.application.service;

import com.ninsky.cronos.application.response.core.MeasurementUnitOptionResponse;
import com.ninsky.cronos.application.response.core.MeasurementUnitResponse;
import com.ninsky.cronos.application.response.core.UnitTypeResponse;
import com.ninsky.cronos.domain.entity.enums.UnitDimension;
import com.ninsky.cronos.domain.model.core.MeasurementUnit;
import com.ninsky.cronos.domain.model.core.MeasurementUnitView;
import com.ninsky.cronos.domain.model.core.UnitType;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Domain → API shapes and the audit snapshots of the unit catalog, kept in one place so the REST
 * services and the .xlsx import describe a record identically.
 */
public final class UnitCatalogResponseMapper {

    private UnitCatalogResponseMapper() {
    }

    public static UnitTypeResponse toResponse(UnitType unitType) {
        return UnitTypeResponse.builder()
                .id(unitType.getId())
                .codeIdentity(unitType.getCodeIdentity())
                .name(unitType.getName())
                .dimension(nameOf(unitType.getDimension()))
                .status(unitType.getStatus().name())
                .createdAt(unitType.getCreatedAt())
                .createdBy(unitType.getCreatedBy())
                .updatedAt(unitType.getUpdatedAt())
                .updatedBy(unitType.getUpdatedBy())
                .build();
    }

    public static MeasurementUnitResponse toResponse(MeasurementUnitView view, boolean inUse) {
        MeasurementUnit unit = view.unit();
        return MeasurementUnitResponse.builder()
                .id(unit.getId())
                .codeIdentity(unit.getCodeIdentity())
                .name(unit.getName())
                .namePlural(unit.getNamePlural())
                .unitTypeId(unit.getUnitTypeId())
                .unitTypeCode(view.unitTypeCode())
                .unitType(view.unitTypeName())
                .dimension(nameOf(view.dimension()))
                .multiplierToBase(plain(unit.getMultiplierToBase()))
                .isBaseUnit(unit.isBaseUnit())
                .isSystemDefault(unit.isSystemDefault())
                .inUse(inUse)
                .status(unit.getStatus().name())
                .createdAt(unit.getCreatedAt())
                .createdBy(unit.getCreatedBy())
                .updatedAt(unit.getUpdatedAt())
                .updatedBy(unit.getUpdatedBy())
                .build();
    }

    public static MeasurementUnitOptionResponse toOption(MeasurementUnitView view) {
        MeasurementUnit unit = view.unit();
        return new MeasurementUnitOptionResponse(unit.getId(), unit.getCodeIdentity(), unit.getName(), unit.getNamePlural(),
                unit.getUnitTypeId(), view.unitTypeCode(), view.unitTypeName(), nameOf(view.dimension()),
                plain(unit.getMultiplierToBase()), unit.isBaseUnit());
    }

    public static Map<String, Object> snapshot(UnitType unitType) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("codeIdentity", unitType.getCodeIdentity());
        snapshot.put("name", unitType.getName());
        snapshot.put("dimension", nameOf(unitType.getDimension()));
        snapshot.put("status", unitType.getStatus().name());
        return snapshot;
    }

    public static Map<String, Object> snapshot(MeasurementUnit unit) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("codeIdentity", unit.getCodeIdentity());
        snapshot.put("name", unit.getName());
        snapshot.put("namePlural", unit.getNamePlural());
        snapshot.put("unitTypeId", unit.getUnitTypeId());
        snapshot.put("multiplierToBase", plain(unit.getMultiplierToBase()));
        snapshot.put("isBaseUnit", unit.isBaseUnit());
        snapshot.put("status", unit.getStatus().name());
        return snapshot;
    }

    /**
     * Canonical form: no trailing zeros and never scientific notation (Jackson serializes
     * {@code BigDecimal} via {@code toString()}, which would print {@code 1E+3} for 1000).
     */
    public static BigDecimal plain(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }

    private static String nameOf(UnitDimension dimension) {
        return dimension == null ? null : dimension.name();
    }
}
