package com.ninsky.cronos.application.response.auth;

import lombok.Builder;
import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record UserSessionResponse(
        UUID id,
        String ipAddress,
        String userAgent,
        String browser,
        String os,
        String device,
        String location,
        LocalDateTime lastActivityAt,
        boolean isActive,
        boolean isCurrentSession
) {}
