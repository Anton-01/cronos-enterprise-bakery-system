package com.ninsky.cronos.domain.model.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSession {

    private UUID id;
    private UUID userId;
    private String sessionToken;
    private String deviceId;
    private String ipAddress;
    private String userAgent;
    private String browser;
    private String operatingSystem;
    private String device;
    private String location;
    @Builder.Default
    private boolean isActive = true;
    private LocalDateTime createdAt;
    private LocalDateTime lastActivityAt;
    private LocalDateTime expiresAt;
    private LocalDateTime terminatedAt;
    private String terminationReason;

    /** RFC 7638 JWK thumbprint of the DPoP key this session was bound to at login; null if unbound. Immutable for the session's lifetime. */
    private String dpopJkt;
}
