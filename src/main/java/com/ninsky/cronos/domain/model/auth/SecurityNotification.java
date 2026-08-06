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
public class SecurityNotification {
    private UUID id;
    private UUID userId;
    private String type;
    private String title;
    private String message;
    private String severity;
    private String deviceName;
    private String ipAddress;
    private String location;
    private String browser;
    private String operatingSystem;
    private boolean read;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
    private boolean emailSent;
    private LocalDateTime emailSentAt;
}
