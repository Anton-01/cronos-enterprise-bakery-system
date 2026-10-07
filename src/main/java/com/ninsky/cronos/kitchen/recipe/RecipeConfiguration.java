package com.ninsky.cronos.kitchen.recipe;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** A configured product of a recipe (§5.6, §6): excluded selectable lines, substitutions, batch size. */
public record RecipeConfiguration(List<UUID> excludedLineIds, List<Substitution> substitutions, BigDecimal yieldQuantity) {

    public RecipeConfiguration {
        excludedLineIds = excludedLineIds == null ? List.of() : List.copyOf(excludedLineIds);
        substitutions = substitutions == null ? List.of() : List.copyOf(substitutions);
    }

    public record Substitution(UUID lineId, UUID ingredientId) {
    }
}
