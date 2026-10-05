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
public class LoginHistory {
    private UUID id;
    private UUID userId;
    private LocalDateTime loginAt;
    private String ipAddress;
    private String status;
    /** SUCCESS | FAILURE | LOCKED | TWO_FACTOR_FAILED (spec §3.8). */
    private String outcome;
    private String userAgent;
    private String browser;
    private String operatingSystem;
    private String device;
    private String location;
    private boolean successful;
    private String failureReason;
    private Boolean twoFactorUsed;
    private LocalDateTime createdAt;
}
