package com.ninsky.cronos.domain.model.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Pure domain aggregate — no JPA, no Spring Security. References {@link Role} by id only
 * ({@code roleIds}). Deliberately does NOT implement {@code UserDetails} (a framework interface
 * can't live on a pure domain model); {@code CronosUserPrincipal} in infrastructure/security
 * bridges this for Spring Security, backed by the separate {@code AuthUserProjection} read model.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    private UUID id;
    private String username;
    private String email;
    @Builder.Default
    private boolean emailVerified = false;
    private String password;
    @Builder.Default
    private Set<Long> roleIds = new HashSet<>();
    @Builder.Default
    private boolean enabled = true;
    @Builder.Default
    private boolean accountNonLocked = true;
    @Builder.Default
    private boolean accountNonExpired = true;
    @Builder.Default
    private boolean credentialsNonExpired = true;
    @Builder.Default
    private boolean twoFactorEnabled = false;
    private String twoFactorSecret;
    @Builder.Default
    private int failedLoginAttempts = 0;
    private LocalDateTime lockedUntil;
    private LocalDateTime lastFailedLogin;
    private LocalDateTime passwordChangedAt;
    private LocalDateTime lastLoginAt;
    @Builder.Default
    private boolean passwordNeedsChange = false;
    /** Content-addressed avatar object key ({@code avatars/{id}/{hash}.jpg}), null when no avatar. */
    private String avatarKey;
    /**
     * Optimistic-lock version. Must round-trip through every load/save: {@code UserRepositoryAdapter}
     * rebuilds the entity from this aggregate, and a null version on an existing id would be treated
     * as a new row.
     */
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public void incrementFailedAttempts() {
        this.failedLoginAttempts++;
    }

    public void lockAccount(int durationMinutes) {
        this.accountNonLocked = false;
        this.lockedUntil = LocalDateTime.now().plusMinutes(durationMinutes);
    }

    public void resetFailedAttempts() {
        this.failedLoginAttempts = 0;
        this.accountNonLocked = true;
        this.lockedUntil = null;
    }

    public void updateLastLogin() {
        this.lastLoginAt = LocalDateTime.now();
        this.failedLoginAttempts = 0;
    }

    /** Read-only "is actually locked right now" check, accounting for {@code lockedUntil} expiry, without mutating state. */
    public boolean isCurrentlyLocked() {
        if (!accountNonLocked && lockedUntil != null && lockedUntil.isBefore(LocalDateTime.now())) {
            return false;
        }
        return !accountNonLocked;
    }
}
