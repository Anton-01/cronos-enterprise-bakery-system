package com.ninsky.cronos.iam.policy;

import java.util.UUID;

/** Whether a user must enrol TOTP: their own flag or a role listed in the policy. */
public interface TwoFactorRequirement {

    boolean isRequired(UUID userId);
}
