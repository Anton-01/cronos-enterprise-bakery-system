package com.ninsky.cronos.application.request.recipe;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
public record CreateRecipeShareRequest(
        @NotNull(message = "Debes especificar la duración en días")
        @Min(value = 1, message = "El mínimo es 1 día")
        @Max(value = 30, message = "Por seguridad, el máximo es 30 días")
        Integer expirationDays,

        @Email(message = "Formato de correo inválido")
        String recipientEmail
) {}
