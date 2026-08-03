package com.ninsky.cronos.application.request.recipe;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.util.UUID;

@Builder
public record SubstituteIngredientRequest(
        @NotNull(message = "El material sustituto es obligatorio")
        UUID substituteMaterialId
) {}
