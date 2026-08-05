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
public class RefreshToken {

    private UUID id;
    private String token;
    private UUID userId;
    private UUID sessionId;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    @Builder.Default
    private boolean revoked = false;
    private LocalDateTime revokedAt;
    private String ipAddress;
    private String userAgent;
}
