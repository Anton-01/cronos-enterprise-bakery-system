package com.ninsky.cronos.kitchen.allergen;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

/** {@code POST /allergens/detect}. */
public record DetectRequest(
        @NotNull(message = "{api.validation.required}") @Size(max = 5000, message = "{kitchen.detect.text.maxLength}") String text,
        Set<Long> excludeIds
) {
}
