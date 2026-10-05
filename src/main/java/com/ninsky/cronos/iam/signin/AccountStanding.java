package com.ninsky.cronos.iam.signin;

import com.ninsky.cronos.iam.policy.SecurityPolicy;
import com.ninsky.cronos.iam.user.UserStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Lifecycle facts that decide whether a user may sign in (spec §3.3, §8). */
public record AccountStanding(
        UUID userId,
        UserStatus status,
        Instant statusUntil,
        LocalDate accessExpiresAt,
        boolean passwordNeedsChange,
        Instant passwordChangedAt,
        int failedLoginAttempts,
        String locale
) {

    /** LOCKED with an {@code until} already in the past. */
    public boolean lockExpired(Instant now) {
        return status == UserStatus.LOCKED && statusUntil != null && !statusUntil.isAfter(now);
    }

    /** Access ends once {@code accessExpiresAt} is reached on the tenant calendar (spec §3.3). */
    public boolean accessExpired(LocalDate today) {
        return accessExpiresAt != null && !today.isBefore(accessExpiresAt);
    }

    /** Forced change, or the password is older than the policy allows. */
    public boolean mustChangePassword(SecurityPolicy policy, Instant now) {
        return passwordNeedsChange || policy.passwordMaxAgeDays() > 0 && passwordChangedAt != null
                && passwordChangedAt.plus(policy.passwordMaxAgeDays(), ChronoUnit.DAYS).isBefore(now);
    }
}
