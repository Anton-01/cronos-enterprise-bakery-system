package com.ninsky.cronos.application.response.roles;

import lombok.Builder;

@Builder
public record PermissionResponse(
        Long id,
        String name,
        String description,
        String resource,
        String action
) {}