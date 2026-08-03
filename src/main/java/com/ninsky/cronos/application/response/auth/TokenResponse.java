package com.ninsky.cronos.application.response.auth;

import lombok.Builder;

@Builder
public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        Integer expiresIn
) { }
