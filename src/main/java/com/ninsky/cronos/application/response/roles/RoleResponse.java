package com.ninsky.cronos.application.response.roles;

import lombok.Builder;
import java.util.Set;

@Builder
public record RoleResponse(
        Long id,
        String name,
        String description,
        Set<PermissionResponse> permissions
) {}
