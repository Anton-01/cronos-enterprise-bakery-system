package com.ninsky.cronos.kitchen.shared;

import jakarta.validation.constraints.NotNull;

/** {@code PATCH …/status} body for catalog rows. */
public record StatusRequest(
        @NotNull(message = "{api.validation.required}") KitchenStatus status,
        @NotNull(message = "{api.validation.required}") Long version
) {
}
