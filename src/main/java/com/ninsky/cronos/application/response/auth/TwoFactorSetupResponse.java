package com.ninsky.cronos.application.response.auth;

import lombok.Builder;

@Builder
public record TwoFactorSetupResponse(
        String secret,
        String qrCodeUrl,
        String message
) { }
