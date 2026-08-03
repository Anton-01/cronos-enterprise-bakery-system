package com.ninsky.cronos.application.request.core.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record RefreshTokenRequest(
        @NotBlank(message = "Refresh token is required")
        String refreshToken
) { }
