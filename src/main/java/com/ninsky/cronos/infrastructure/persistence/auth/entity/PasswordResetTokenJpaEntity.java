package com.ninsky.cronos.infrastructure.persistence.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Pre-existing bug fixed here: the original entity had a DB-side {@code gen_random_uuid()} default
 * with no {@code @GeneratedValue}, which made every {@code persist()} fail with "Identifier must be
 * manually assigned" (Hibernate requires an id before persist unless it owns generation). This was
 * never caught before because forgot/reset-password were 401ing on the {@code SecurityConfig}
 * permitAll gap fixed elsewhere in this phase — fixing that gap is what surfaced this. Switching to
 * Hibernate's app-side UUID generation keeps the same column type with no schema change.
 */
@Entity @Getter @Setter @NoArgsConstructor
@Table(name = "password_reset_tokens", schema = "public", indexes = @Index(name = "idx_password_reset_token", columnList = "token"))
public class PasswordResetTokenJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, unique = true, length = 500)
    private String token;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private boolean used;

    @Column(name = "created_at", columnDefinition = "timestamp without time zone DEFAULT CURRENT_TIMESTAMP", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
