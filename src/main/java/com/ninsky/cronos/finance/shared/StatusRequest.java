package com.ninsky.cronos.finance.shared;

import jakarta.validation.constraints.NotNull;

/** {@code PATCH …/status} body. */
public record StatusRequest(
        @NotNull(message = "{api.validation.required}") FinanceStatus status,
        @NotNull(message = "{api.validation.required}") Long version
) {
}
