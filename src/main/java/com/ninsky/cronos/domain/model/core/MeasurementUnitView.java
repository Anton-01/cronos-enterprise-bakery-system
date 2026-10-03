package com.ninsky.cronos.domain.model.core;

import com.ninsky.cronos.domain.entity.enums.UnitDimension;

/**
 * Read model: a {@link MeasurementUnit} together with the display fields of its {@link UnitType},
 * loaded in a single query (entity graph) so list endpoints never issue one unit-type lookup per row.
 */
public record MeasurementUnitView(
        MeasurementUnit unit,
        String unitTypeCode,
        String unitTypeName,
        UnitDimension dimension
) {
}
