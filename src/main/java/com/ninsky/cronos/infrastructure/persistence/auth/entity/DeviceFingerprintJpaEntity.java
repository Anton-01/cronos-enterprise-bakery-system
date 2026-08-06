package com.ninsky.cronos.infrastructure.persistence.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "device_fingerprints", schema = "public",
        uniqueConstraints = @UniqueConstraint(name = "uq_user_fingerprint", columnNames = {"user_id", "fingerprint_hash"}))
public class DeviceFingerprintJpaEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "fingerprint_hash", nullable = false, length = 255)
    private String fingerprintHash;

    @Column(name = "device_name", length = 200)
    private String deviceName;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(length = 100)
    private String browser;

    @Column(name = "operating_system", length = 100)
    private String operatingSystem;

    @Column(name = "device_type", length = 50)
    private String deviceType;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(length = 100)
    private String location;

    @Column(name = "is_trusted", nullable = false)
    private boolean trusted;

    @Column(name = "first_seen_at", nullable = false)
    private LocalDateTime firstSeenAt;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    @Column(name = "trusted_at")
    private LocalDateTime trustedAt;

    @Column(name = "login_count", nullable = false, columnDefinition = "integer DEFAULT 0")
    private int loginCount;
}
