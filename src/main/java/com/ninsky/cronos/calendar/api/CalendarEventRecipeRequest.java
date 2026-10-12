package com.ninsky.cronos.calendar.api;

import com.ninsky.cronos.kitchen.recipe.RecipeConfiguration;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/** A recipe to produce for an entry; the recipe must be visible to the caller (own or library). */
public record CalendarEventRecipeRequest(@NotNull UUID recipeId,
                                         @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("10000") @Digits(integer = 5, fraction = 2)
                                         BigDecimal quantity,
                                         RecipeConfiguration recipeConfiguration,
                                         @Size(max = 300) String notes) {
}
