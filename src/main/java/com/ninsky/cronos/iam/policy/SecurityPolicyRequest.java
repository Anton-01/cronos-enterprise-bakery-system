package com.ninsky.cronos.iam.policy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Body of {@code PUT /iam/security-policy}: every field required, checked by {@link SecurityPolicyRules}. */
public record SecurityPolicyRequest(
        Integer passwordMinLength,
        Boolean passwordRequireUppercase,
        Boolean passwordRequireLowercase,
        Boolean passwordRequireDigit,
        Boolean passwordRequireSymbol,
        Integer passwordHistory,
        Integer passwordMaxAgeDays,
        Integer maxFailedAttempts,
        Integer lockoutMinutes,
        Integer sessionIdleMinutes,
        Integer sessionAbsoluteHours,
        Integer maxConcurrentSessions,
        Integer invitationTtlHours,
        List<Long> twoFactorRequiredRoleIds,
        Long version
) {

    /** Only valid after {@link SecurityPolicyRules#validate} passed. */
    public SecurityPolicy toPolicy(Instant updatedAt, UUID updatedBy) {
        return new SecurityPolicy(passwordMinLength, passwordRequireUppercase, passwordRequireLowercase, passwordRequireDigit,
                passwordRequireSymbol, passwordHistory, passwordMaxAgeDays, maxFailedAttempts, lockoutMinutes,
                sessionIdleMinutes, sessionAbsoluteHours, maxConcurrentSessions, invitationTtlHours,
                twoFactorRequiredRoleIds.stream().distinct().sorted().toList(), updatedAt, updatedBy, version + 1);
    }
}
