package com.ninsky.cronos.kitchen.ingredient;

import com.ninsky.cronos.kitchen.costing.PriceSource;
import com.ninsky.cronos.kitchen.shared.AllergenRef;
import com.ninsky.cronos.kitchen.shared.Dimension;
import com.ninsky.cronos.kitchen.shared.KitchenStatus;
import com.ninsky.cronos.kitchen.shared.Scope;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** §4.1 list row; {@code costPerBaseUnit} in the default currency. */
public record IngredientSummary(UUID id, String code, String name, Long categoryId, String categoryName, Scope scope,
                                Dimension baseDimension, String baseUnitCode, BigDecimal yieldPercent, BigDecimal costPerBaseUnit,
                                PriceSource priceSource, LocalDate pricedAt, boolean priceStale, List<AllergenRef> allergens,
                                long usedInRecipes, KitchenStatus status) {
}
