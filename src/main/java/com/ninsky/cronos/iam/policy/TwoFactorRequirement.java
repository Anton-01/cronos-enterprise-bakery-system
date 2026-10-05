package com.ninsky.cronos.iam.policy;

import java.util.List;
import java.util.UUID;

/**
 * Whether a user must use TOTP: their own flag or an ACTIVE role listed in the policy. SUPER_ADMIN
 * never counts as such a role (contract §8.3). Always read from the database, never from token claims.
 */
public interface TwoFactorRequirement {

    boolean isRequired(UUID userId);

    /** Required but not enrolled: the enrolment gate (§8.1) blocks this user. */
    boolean mustEnrol(UUID userId);

    /** Display names of the roles that make 2FA mandatory for the user. */
    List<String> requiredBy(UUID userId);
}
