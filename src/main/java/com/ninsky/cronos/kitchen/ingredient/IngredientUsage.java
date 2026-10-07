package com.ninsky.cronos.kitchen.ingredient;

import java.math.BigDecimal;
import java.util.UUID;

/** A caller recipe line using the ingredient. */
public record IngredientUsage(UUID id, String name, BigDecimal quantity, String unitCode) {
}
