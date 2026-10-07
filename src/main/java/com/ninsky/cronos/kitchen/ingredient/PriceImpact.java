package com.ninsky.cronos.kitchen.ingredient;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** §4.5 ripple report; {@code status} PENDING when recipes were queued instead of recalculated inline. */
public record PriceImpact(IngredientSummary ingredient, int recipesAffected, int quotesFlagged, List<BelowMargin> recipesBelowMargin,
                          Status status) {

    public enum Status {
        DONE,
        PENDING
    }

    public record BelowMargin(UUID id, String name, BigDecimal marginPercent) {
    }
}
