package com.ninsky.cronos.application.request.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.Set;

public record AdminUserCreateRequest(
        @NotBlank(message = "El nombre de usuario es obligatorio")
        String username,

        @NotBlank(message = "El email es obligatorio")
        @Email(message = "Email inválido")
        String email,

        @NotEmpty(message = "Debes asignar al menos un rol")
        Set<Long> roleIds,

        String firstName,
        String lastName,
        String phone
) {}
