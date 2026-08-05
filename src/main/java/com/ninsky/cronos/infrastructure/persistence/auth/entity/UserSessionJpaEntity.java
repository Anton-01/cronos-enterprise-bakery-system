package com.ninsky.cronos.infrastructure.persistence.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name = "user_sessions")
public class UserSessionJpaEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "session_token", nullable = false, unique = true, length = 500)
    private String sessionToken;

    @Column(name = "device_id") private String deviceId;
    @Column(name = "ip_address") private String ipAddress;
    @Column(name = "user_agent") private String userAgent;
    private String browser;
    @Column(name = "operating_system") private String operatingSystem;
    private String device;
    private String location;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "last_activity_at") private LocalDateTime lastActivityAt;
    @Column(name = "expires_at", nullable = false) private LocalDateTime expiresAt;
    @Column(name = "terminated_at") private LocalDateTime terminatedAt;
    @Column(name = "termination_reason") private String terminationReason;

    @Column(name = "dpop_jkt") private String dpopJkt;
}
