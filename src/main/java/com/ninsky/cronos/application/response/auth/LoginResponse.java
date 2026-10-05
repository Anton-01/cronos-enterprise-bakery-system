package com.ninsky.cronos.application.response.auth;

import com.ninsky.cronos.application.response.menu.MenuItemResponse;
import lombok.Builder;
import java.util.List;

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
        /** True when the password must be changed before using the app (forced or past the policy max age). */
        Boolean mustChangePassword,
        String message
) { }