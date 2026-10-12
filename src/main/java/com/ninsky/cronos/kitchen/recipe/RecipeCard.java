package com.ninsky.cronos.kitchen.recipe;

import com.ninsky.cronos.kitchen.shared.AllergenRef;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Compact projection of a recipe linked from another module (calendar orders): resolved at read time, so a renamed
 * recipe shows its current name.
 */
public record RecipeCard(UUID id, String name, String coverImageUrl, BigDecimal yieldQuantity, String yieldUnit,
                         List<AllergenRef> allergens) {

    public RecipeCard {
        allergens = allergens == null ? List.of() : List.copyOf(allergens);
    }
}
