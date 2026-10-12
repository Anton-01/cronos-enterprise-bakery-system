package com.ninsky.cronos.application.response.auth;

import com.ninsky.cronos.application.response.menu.MenuItemResponse;
import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        Integer expiresIn,
        String username,
        String email,
        List<String> roles,
        /** URN-style policy strings (e.g. "urn:cronos:recipe:read") derived from the user's granted permissions. */
        List<String> policies,
        /** DB-driven nav tree, pruned to what the user's granted permissions unlock. */
        List<MenuItemResponse> navigation,
        Boolean requiresTwoFactor,
        /** Answer it with POST /auth/login/two-factor (§2.3.4); null when no second step is pending. */
        UUID challengeId,
        /** True when the password must be changed before using the app (forced or past the policy max age). */
        Boolean mustChangePassword,
        /** Same fact under the name of the §2.2 contract. */
        Boolean passwordChangeRequired,
        /** Idle minutes before the UI locks the screen; null/0 = disabled (§2.7). */
        Integer screenLockMinutes,
        /** Contract §8.1: 2FA is mandatory and not enrolled yet, so the UI routes straight to setup. */
        Boolean requiresTwoFactorEnrollment,
        String message,
        /**
         * Additive (performance-latency §5.1): the signed-in user's profile, same shape as {@code GET /users/me} data,
         * so the UI does not call it right after sign-in. Null while a second factor is pending.
         */
        UserResponse user
) { }
