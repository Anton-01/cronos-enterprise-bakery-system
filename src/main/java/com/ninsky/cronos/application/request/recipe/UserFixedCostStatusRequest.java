package com.ninsky.cronos.application.request.recipe;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

/** {@code PATCH /user-fixed-cost/{id}/status}. */
public record UserFixedCostStatusRequest(@NotNull @JsonProperty("isActive") Boolean isActive) {
}
