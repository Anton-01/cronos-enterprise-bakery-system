package com.ninsky.cronos.iam.policy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The singleton security policy (spec §8). */
public record SecurityPolicy(
        int passwordMinLength,
        boolean passwordRequireUppercase,
        boolean passwordRequireLowercase,
        boolean passwordRequireDigit,
        boolean passwordRequireSymbol,
        int passwordHistory,
        int passwordMaxAgeDays,
        int maxFailedAttempts,
        int lockoutMinutes,
        int sessionIdleMinutes,
        int sessionAbsoluteHours,
        int maxConcurrentSessions,
        int invitationTtlHours,
        List<Long> twoFactorRequiredRoleIds,
        Instant updatedAt,
        UUID updatedBy,
        long version
) {
    public SecurityPolicy {
        twoFactorRequiredRoleIds = List.copyOf(twoFactorRequiredRoleIds);
    }
}
