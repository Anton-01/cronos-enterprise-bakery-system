package com.ninsky.cronos.application.request.core.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record LoginRequest(
        @NotBlank(message = "{validation.username.required}")
        String username,

        @NotBlank(message = "{validation.password.required}")
        String password,

        /** A 6-digit TOTP or a recovery code {@code XXXX-XXXX}. */
        String twoFactorCode
) {
        public LoginRequest {
                username = username != null ? username.trim() : null;
                password = password != null ? password.trim() : null;
        }

        @Override
        public String toString() {
                return "LoginRequest[username=" + username + "]";
        }
}
