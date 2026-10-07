package com.ninsky.cronos.kitchen.job;

import java.util.List;
import java.util.UUID;

/**
 * Payload of a {@code RECALCULATE_RECIPES} job: explicit {@code recipeIds}, or (when null) every live recipe
 * using {@code ingredientId} whose owner has no own price for it (reference-price ripple, §4.5 step 5).
 */
public record RecalculationJob(UUID ingredientId, List<UUID> recipeIds, String reasonKey, List<Object> reasonArgs, UUID actor) {
}
