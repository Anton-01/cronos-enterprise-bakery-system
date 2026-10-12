package com.ninsky.cronos.identity.domain;

import java.time.Duration;

/**
 * Sign-in, lockout and self-registration settings of the security policy (§2.3, §2.4, §2.7).
 *
 * @param screenLockMinutes  idle minutes before the UI locks the screen; 0 disables it
 * @param lockMemoryHours    a lock count decays to 0 after this many hours without a new lock
 */
public record SignInPolicy(int maxFailedAttempts, int lockoutMinutes, int lockoutBackoffMultiplier, int lockoutMaxMinutes,
                           int failureWindowMinutes, int screenLockMinutes, int rememberMeDays, boolean requireEmailVerification,
                           int registrationTtlDays, boolean disallowUserInfo, int lockMemoryHours) {

    /** Defaults of §9 (used when the policy row is missing). */
    public static final SignInPolicy DEFAULTS = new SignInPolicy(5, 15, 2, 1440, 15, 15, 30, true, 30, true, 24);

    /** The progressive lock schedule. */
    public LockoutSchedule schedule() {
        return LockoutSchedule.of(lockoutMinutes, lockoutBackoffMultiplier, lockoutMaxMinutes);
    }

    public Duration failureWindow() {
        return Duration.ofMinutes(failureWindowMinutes);
    }

    public Duration rememberMe() {
        return Duration.ofDays(rememberMeDays);
    }

    public Duration registrationTtl() {
        return Duration.ofDays(registrationTtlDays);
    }

    public Duration lockMemory() {
        return Duration.ofHours(lockMemoryHours);
    }
}
