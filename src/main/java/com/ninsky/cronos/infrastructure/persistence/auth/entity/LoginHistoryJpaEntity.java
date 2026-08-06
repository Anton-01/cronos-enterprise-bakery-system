package com.ninsky.cronos.infrastructure.persistence.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "login_history")
public class LoginHistoryJpaEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "login_at", nullable = false)
    private LocalDateTime loginAt;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "status", length = 15)
    private String status;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(length = 100)
    private String browser;

    @Column(name = "operating_system", length = 100)
    private String operatingSystem;

    @Column(length = 100)
    private String device;

    @Column(length = 100)
    private String location;

    @Column(nullable = false)
    private boolean successful;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "two_factor_used")
    private Boolean twoFactorUsed;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
