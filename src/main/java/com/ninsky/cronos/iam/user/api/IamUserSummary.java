package com.ninsky.cronos.iam.user.api;

import com.ninsky.cronos.iam.shared.RoleRef;
import com.ninsky.cronos.iam.user.UserStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** List row of {@code GET /iam/users} (spec §3.1). */
public record IamUserSummary(
        UUID id,
        String username,
        String email,
        String firstName,
        String lastName,
        String displayName,
        String avatarUrl,
        String jobTitle,
        String department,
        UserStatus status,
        List<RoleRef> roles,
        boolean twoFactorEnabled,
        boolean mustChangePassword,
        Instant lastLoginAt,
        Instant statusUntil,
        LocalDate accessExpiresAt,
        Instant createdAt
) {
}
