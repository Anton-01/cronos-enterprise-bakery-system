package com.ninsky.cronos.application.response.auth;

import lombok.Builder;
import java.time.LocalDateTime;

@Builder
public record LoginHistoryResponse(
        String ipAddress,
        String userAgent,
        String status, // Ej: "SUCCESS", "FAILED"
        String failureReason,
        LocalDateTime createdAt
) {}
