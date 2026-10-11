package com.ninsky.cronos.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code POST /auth/email/verify}. */
public record VerifyEmailRequest(@NotBlank @Size(max = 100) String token) {

    @Override
    public String toString() {
        return "VerifyEmailRequest[]";
    }
}
