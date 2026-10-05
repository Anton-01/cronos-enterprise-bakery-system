package com.ninsky.cronos.iam.user;

import com.ninsky.cronos.iam.shared.UserRef;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One user with profile columns, email and phone already decrypted. */
public record UserRow(
        UUID id, String username, String email, String firstName, String lastName, String avatarKey,
        String jobTitle, String department, String employeeNumber, String phoneNumber, String locale,
        UserStatus status, StatusReason statusReason, String statusComment, Instant statusUntil,
        Instant statusChangedAt, UUID statusChangedBy, LocalDate accessExpiresAt, boolean requireTwoFactor,
        boolean twoFactorEnabled, boolean mustChangePassword, boolean emailVerified, int failedLoginAttempts,
        Instant lastLoginAt, Instant passwordChangedAt, Instant createdAt, UUID createdById,
        Instant updatedAt, UUID updatedById, long version
) {
    public String displayName() {
        return UserRef.displayName(firstName, lastName, username);
    }
}
