package com.ninsky.cronos.identity.api;

import jakarta.validation.constraints.Size;

/** {@code POST /auth/logout}: the refresh token of the session to end; works without an access token. */
public record LogoutRequest(@Size(max = 500) String refreshToken) {

    @Override
    public String toString() {
        return "LogoutRequest[]";
    }
}
