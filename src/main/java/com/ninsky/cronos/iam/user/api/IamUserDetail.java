package com.ninsky.cronos.iam.user.api;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.ninsky.cronos.iam.shared.UserRef;
import com.ninsky.cronos.iam.user.StatusReason;

import java.time.Instant;

/** {@code IamUserSummary} plus the detail fields (spec §3.1). */
public record IamUserDetail(
        @JsonUnwrapped IamUserSummary summary,
        String phoneNumber,
        String employeeNumber,
        String locale,
        boolean emailVerified,
        int failedLoginAttempts,
        Instant passwordChangedAt,
        StatusReason statusReason,
        String statusComment,
        Instant statusChangedAt,
        UserRef statusChangedBy,
        Instant invitationExpiresAt,
        UserRef createdBy,
        Instant updatedAt,
        UserRef updatedBy,
        boolean requireTwoFactor,
        long version
) {
}
