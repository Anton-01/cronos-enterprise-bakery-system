package com.ninsky.cronos.kitchen.ingredient;

/** {@code GET /ingredients/stats} over visible ACTIVE rows. */
public record IngredientStats(long total, long system, long own, long withAllergens, long stalePrices, long unpriced) {
}
