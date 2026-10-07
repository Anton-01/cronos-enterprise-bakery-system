package com.ninsky.cronos.kitchen.recipe;

/** {@code GET /recipes/stats} over the caller's live recipes. */
public record RecipeStats(long total, long active, long drafts, long staleCost, long belowMargin) {
}
