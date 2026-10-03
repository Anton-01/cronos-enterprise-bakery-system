package com.ninsky.cronos.domain.port.core;

import com.ninsky.cronos.domain.entity.enums.RecordStatus;
import com.ninsky.cronos.domain.entity.enums.UnitDimension;

/**
 * Optional list filters; every {@code null} field is ignored. {@code search} matches code or name,
 * case-insensitively, as a substring.
 */
public record UnitTypeSearchCriteria(String search, UnitDimension dimension, RecordStatus status) {

    public UnitTypeSearchCriteria {
        search = search == null || search.isBlank() ? null : search.trim();
    }
}
