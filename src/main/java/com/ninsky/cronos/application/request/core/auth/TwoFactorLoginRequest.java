package com.ninsky.cronos.application.request.core.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Second step of a sign-in (§2.3.4): a 6-digit TOTP or a recovery code against the challenge. */
public record TwoFactorLoginRequest(@NotNull UUID challengeId, @NotBlank @Size(max = 20) String code, Boolean rememberMe) {

    @Override
    public String toString() {
        return "TwoFactorLoginRequest[challengeId=" + challengeId + "]";
    }
}
