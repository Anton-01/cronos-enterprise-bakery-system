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
public class PasswordResetToken {

    private UUID id;
    private String token;
    private UUID userId;
    private LocalDateTime expiresAt;
    @Builder.Default
    private boolean used = false;
    private LocalDateTime createdAt;
}
