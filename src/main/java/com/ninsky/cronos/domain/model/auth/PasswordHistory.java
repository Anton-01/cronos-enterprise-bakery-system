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
public class PasswordHistory {

    private UUID id;
    private UUID userId;
    private String passwordHash;
    private LocalDateTime changedAt;
}
