package com.ninsky.cronos.kitchen.recipe;

import java.math.BigDecimal;
import java.util.UUID;

/** {@code GET /recipes/simple} row (quote search). */
public record RecipeOption(UUID id, String name, String description, BigDecimal totalCost, BigDecimal costPerUnit, String yieldUnit) {
}
