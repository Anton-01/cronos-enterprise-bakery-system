package com.ninsky.cronos.application.request.roles;

import jakarta.validation.constraints.NotBlank;
import java.util.Set;

public record RoleRequest(
        @NotBlank(message = "El nombre del rol es obligatorio")
        String name,
        String description,
        Set<Long> permissionIds
) {}
