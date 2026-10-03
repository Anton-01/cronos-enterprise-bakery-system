package com.ninsky.cronos.domain.port.core;

import java.util.Collection;
import java.util.Set;

/**
 * Which units are referenced by business data (raw-material purchase units, recipe ingredient
 * lines, ingredient density rules). A referenced unit's conversion semantics are frozen and it
 * cannot be deleted: changing them would silently restate every cost already computed with it.
 */
public interface MeasurementUnitUsagePort {

    Set<Long> findReferencedUnitIds(Collection<Long> unitIds);

    default boolean isReferenced(Long unitId) {
        return !findReferencedUnitIds(Set.of(unitId)).isEmpty();
    }
}
