package com.ninsky.cronos.domain.model.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A density/volume-to-mass conversion rule for one raw material. References the two
 * {@link MeasurementUnit}s by id only, consistent with the rest of this module.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngredientConversion {

    private Long id;
    private UUID ingredientId;
    private Long volumeUnitId;
    private Long massUnitId;
    private BigDecimal factor;
    private Long userId;
}
