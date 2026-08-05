package com.ninsky.cronos.infrastructure.persistence.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name = "refresh_tokens")
public class RefreshTokenJpaEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 500)
    private String token;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "expires_at", nullable = false) private LocalDateTime expiresAt;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;

    @Column(nullable = false) private boolean revoked = false;
    @Column(name = "revoked_at") private LocalDateTime revokedAt;

    @Column(name = "ip_address") private String ipAddress;
    @Column(name = "user_agent") private String userAgent;
}
