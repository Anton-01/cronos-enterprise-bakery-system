package com.ninsky.cronos.application.response.auth;

import lombok.Builder;
import java.util.List;

@Builder
public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        Integer expiresIn,
        String username,
        String email,
        List<String> roles,
        Boolean requiresTwoFactor,
        String message
) { }