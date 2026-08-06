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
public class DeviceFingerprint {
    private UUID id;
    private UUID userId;
    private String fingerprintHash;
    private String deviceName;
    private String userAgent;
    private String browser;
    private String operatingSystem;
    private String deviceType;
    private String ipAddress;
    private String location;
    private boolean trusted;
    private LocalDateTime firstSeenAt;
    private LocalDateTime lastSeenAt;
    private LocalDateTime trustedAt;
    private int loginCount;
}
