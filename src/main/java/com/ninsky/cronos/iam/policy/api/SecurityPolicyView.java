package com.ninsky.cronos.iam.policy.api;

import com.ninsky.cronos.iam.policy.SecurityPolicy;
import com.ninsky.cronos.iam.shared.UserRef;

import java.time.Instant;
import java.util.List;

/** {@code SecurityPolicy} of the contract (spec §8). */
public record SecurityPolicyView(
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
        UserRef updatedBy,
        long version
) {
    public static SecurityPolicyView of(SecurityPolicy p, UserRef updatedBy) {
        return new SecurityPolicyView(p.passwordMinLength(), p.passwordRequireUppercase(), p.passwordRequireLowercase(),
                p.passwordRequireDigit(), p.passwordRequireSymbol(), p.passwordHistory(), p.passwordMaxAgeDays(),
                p.maxFailedAttempts(), p.lockoutMinutes(), p.sessionIdleMinutes(), p.sessionAbsoluteHours(),
                p.maxConcurrentSessions(), p.invitationTtlHours(), p.twoFactorRequiredRoleIds(), p.updatedAt(), updatedBy,
                p.version());
    }
}
