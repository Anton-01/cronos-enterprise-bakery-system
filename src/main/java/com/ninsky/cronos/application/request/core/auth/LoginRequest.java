package com.ninsky.cronos.application.request.core.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record LoginRequest(
        @NotBlank(message = "{validation.username.required}")
        String username,

        @NotBlank(message = "{validation.password.required}")
        String password,

        Integer twoFactorCode
) {
        public LoginRequest {
                username = username != null ? username.trim() : null;
                password = password != null ? password.trim() : null;
        }
}
