package com.ninsky.cronos.domain.port.core;

import com.ninsky.cronos.domain.entity.enums.RecordStatus;
import com.ninsky.cronos.domain.entity.enums.UnitDimension;

/**
 * Optional list filters; every {@code null} field is ignored. {@code search} matches code, name or
 * plural name, case-insensitively, as a substring.
 */
public record MeasurementUnitSearchCriteria(String search, Long unitTypeId, UnitDimension dimension, RecordStatus status) {

    public MeasurementUnitSearchCriteria {
        search = search == null || search.isBlank() ? null : search.trim();
    }
}
