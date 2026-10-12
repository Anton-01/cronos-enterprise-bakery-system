package com.ninsky.cronos.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code POST /auth/password/reset}. */
public record PasswordResetRequest(@NotBlank @Size(max = 100) String token, @NotBlank @Size(max = 200) String newPassword,
                                   @NotBlank @Size(max = 200) String confirmPassword) {

    @Override
    public String toString() {
        return "PasswordResetRequest[]";
    }
}
