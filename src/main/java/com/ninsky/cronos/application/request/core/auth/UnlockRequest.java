package com.ninsky.cronos.application.request.core.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Screen unlock (§2.7): the session's refresh token and the user's password. */
public record UnlockRequest(@NotBlank @Size(max = 500) String refreshToken, @NotBlank @Size(max = 200) String password) {

    @Override
    public String toString() {
        return "UnlockRequest[]";
    }
}
