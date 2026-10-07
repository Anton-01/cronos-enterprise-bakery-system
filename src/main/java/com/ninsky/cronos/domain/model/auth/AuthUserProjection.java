package com.ninsky.cronos.domain.model.auth;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * Slim, read-only projection for the authentication hot path (login + every authenticated
 * request) — deliberately NOT the full {@link User} aggregate, so loading it never touches the
 * full JPA entity graph (profile, audit fields, etc.). Backed by {@code UserAuthCustomRepository}.
 */
public record AuthUserProjection(
        UUID id,
        String username,
        String email,
        String password,
        boolean enabled,
        boolean accountNonLocked,
        boolean accountNonExpired,
        boolean credentialsNonExpired,
        boolean twoFactorEnabled,
        LocalDateTime lockedUntil,
        Set<String> roleNames,
        Set<String> permissionNames,
        String status,
        long accessVersion,
        boolean mustChangePassword
) {
    public static final String STATUS_ACTIVE = "ACTIVE";

    /** Pre-IAM shape: an ACTIVE user at access version 0. */
    public AuthUserProjection(UUID id, String username, String email, String password, boolean enabled, boolean accountNonLocked,
                              boolean accountNonExpired, boolean credentialsNonExpired, boolean twoFactorEnabled,
                              LocalDateTime lockedUntil, Set<String> roleNames, Set<String> permissionNames) {
        this(id, username, email, password, enabled, accountNonLocked, accountNonExpired, credentialsNonExpired,
                twoFactorEnabled, lockedUntil, roleNames, permissionNames, STATUS_ACTIVE, 0, false);
    }

    /** Same auto-heal-on-read semantics as {@link User#isCurrentlyLocked()}, kept independent since this is a separate read model. */
    public boolean isEffectivelyLocked() {
        if (!accountNonLocked && lockedUntil != null && lockedUntil.isBefore(LocalDateTime.now())) {
            return false;
        }
        return !accountNonLocked;
    }
}
