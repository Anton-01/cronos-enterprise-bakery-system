package com.ninsky.cronos.infrastructure.persistence.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "password_history", schema = "public")
public class PasswordHistoryJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "password_hash", nullable = false, length = 500)
    private String passwordHash;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;
}
