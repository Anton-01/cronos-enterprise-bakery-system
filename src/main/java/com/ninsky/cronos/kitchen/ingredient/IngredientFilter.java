package com.ninsky.cronos.kitchen.ingredient;

import com.ninsky.cronos.kitchen.shared.KitchenStatus;
import com.ninsky.cronos.kitchen.shared.Scope;

import java.util.List;

/** Query parameters of {@code GET /ingredients}. */
public record IngredientFilter(String search, List<Long> categoryIds, List<Long> allergenIds, boolean excludeAllergens, Scope scope,
                               Boolean priceStale, KitchenStatus status) {

    public IngredientFilter {
        categoryIds = categoryIds == null ? List.of() : List.copyOf(categoryIds);
        allergenIds = allergenIds == null ? List.of() : List.copyOf(allergenIds);
    }
}
