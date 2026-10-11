package com.ninsky.cronos.calendar.api;

import com.ninsky.cronos.kitchen.recipe.RecipeConfiguration;
import com.ninsky.cronos.kitchen.shared.AllergenRef;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** A recipe of an entry; name, cover, yield unit and allergens are read from the recipe at query time. */
public record CalendarEventRecipeResponse(UUID id, UUID recipeId, String recipeName, String coverImageUrl, BigDecimal quantity,
                                          String yieldUnit, RecipeConfiguration recipeConfiguration, List<AllergenRef> allergens,
                                          String notes) {

    public CalendarEventRecipeResponse {
        allergens = allergens == null ? List.of() : List.copyOf(allergens);
    }
}
