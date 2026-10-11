package com.ninsky.cronos.application.request.core.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

/**
 * Sign-in with a username or an email (§2.2). {@code identifier} wins; {@code username} is the deprecated alias old
 * clients still send (same value).
 *
 * @param twoFactorCode legacy single-request form: a 6-digit TOTP or a recovery code {@code XXXX-XXXX}
 */
@Builder
public record LoginRequest(
        @Size(max = 254) String identifier,
        @Size(max = 254) String username,
        @NotBlank(message = "{validation.password.required}") String password,
        Boolean rememberMe,
        String twoFactorCode
) {
    public LoginRequest {
        identifier = identifier != null ? identifier.trim() : null;
        username = username != null ? username.trim() : null;
        password = password != null ? password.trim() : null;
    }

    /** Legacy three-argument form (username, password, code). */
    public LoginRequest(String username, String password, String twoFactorCode) {
        this(null, username, password, null, twoFactorCode);
    }

    public boolean remember() {
        return Boolean.TRUE.equals(rememberMe);
    }

    @Override
    public String toString() {
        return "LoginRequest[identifier=" + (identifier != null ? identifier : username) + "]";
    }
}
