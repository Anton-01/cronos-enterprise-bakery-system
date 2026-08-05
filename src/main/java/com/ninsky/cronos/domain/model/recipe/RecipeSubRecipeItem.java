package com.ninsky.cronos.domain.model.recipe;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Aggregate-internal child of {@link Recipe} representing "this recipe uses another recipe as an
 * ingredient." References the nested recipe by {@code subRecipeId} only, never a nested
 * {@code Recipe} object — embedding the object would create a literal Java object-graph cycle on
 * top of the underlying self-referential data structure. Resolved on demand via
 * {@code RecipeRepositoryPort} by whichever service needs the actual sub-recipe (e.g. cost
 * calculation, with a cycle guard — see {@code RecipeCalculationService}).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipeSubRecipeItem {
    private UUID id;
    private UUID subRecipeId;
    private BigDecimal quantity;
    private Integer displayOrder;
    private String notes;
    private BigDecimal totalCost;
    @Builder.Default
    private Long version = 0L;
}
