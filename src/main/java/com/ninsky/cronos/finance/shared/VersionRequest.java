package com.ninsky.cronos.finance.shared;

import jakarta.validation.constraints.NotNull;

/** {@code PATCH …/default} body. */
public record VersionRequest(@NotNull(message = "{api.validation.required}") Long version) {
}
