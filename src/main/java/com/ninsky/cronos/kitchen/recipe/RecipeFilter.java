package com.ninsky.cronos.kitchen.recipe;

import com.ninsky.cronos.kitchen.costing.CostStatus;
import com.ninsky.cronos.kitchen.shared.Scope;

import java.util.List;

/** Query parameters of {@code GET /recipes}. */
public record RecipeFilter(String search, List<Long> categoryIds, List<RecipeStatus> statuses, List<Long> freeOfAllergenIds,
                           CostStatus costStatus, Scope scope) {

    public RecipeFilter {
        categoryIds = categoryIds == null ? List.of() : List.copyOf(categoryIds);
        statuses = statuses == null ? List.of() : List.copyOf(statuses);
        freeOfAllergenIds = freeOfAllergenIds == null ? List.of() : List.copyOf(freeOfAllergenIds);
    }
}
