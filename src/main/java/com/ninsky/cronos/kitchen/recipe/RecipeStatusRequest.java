package com.ninsky.cronos.kitchen.recipe;

import jakarta.validation.constraints.NotNull;

/** {@code PATCH /recipes/{id}/status}. */
public record RecipeStatusRequest(
        @NotNull(message = "{api.validation.required}") RecipeStatus status,
        @NotNull(message = "{api.validation.required}") Long version
) {
}
