package com.ninsky.cronos.infrastructure.persistence.auth.entity;

import com.ninsky.cronos.domain.entity.base.AuditableEntity;
import com.ninsky.cronos.infrastructure.persistence.crypto.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Pure persistence mapping — no Spring Security coupling. {@code CronosUserPrincipal}
 * (infrastructure/security) is what Spring Security actually sees; this class never implements
 * {@code UserDetails}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "users")
public class UserJpaEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    /**
     * Ciphertext, non-deterministic (random IV per encryption) — uniqueness can't be enforced on
     * this column. Equality lookups (login, existence checks, admin search) go through
     * {@link #emailBlindIndex} instead; see {@code UserRepositoryAdapter}.
     */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String email;

    /** Deterministic HMAC-SHA256 of the normalized email — the actual uniqueness/lookup key. */
    @Column(name = "email_blind_index", nullable = false, unique = true, length = 44)
    private String emailBlindIndex;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(length = 500)
    private String password;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"), inverseJoinColumns = @JoinColumn(name = "role_id"))
    @Builder.Default
    private Set<RoleJpaEntity> roles = new HashSet<>();

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "account_non_locked", nullable = false)
    private boolean accountNonLocked = true;

    @Column(name = "account_non_expired", nullable = false)
    private boolean accountNonExpired = true;

    @Column(name = "credentials_non_expired", nullable = false)
    private boolean credentialsNonExpired = true;

    @Column(name = "two_factor_enabled", nullable = false)
    private boolean twoFactorEnabled = false;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "two_factor_secret", columnDefinition = "TEXT")
    private String twoFactorSecret;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "last_failed_login")
    private LocalDateTime lastFailedLogin;

    @Column(name = "password_changed_at")
    private LocalDateTime passwordChangedAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "password_needs_change", nullable = false)
    private boolean passwordNeedsChange = false;
}
