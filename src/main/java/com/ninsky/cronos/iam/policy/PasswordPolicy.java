package com.ninsky.cronos.iam.policy;

import java.util.List;
import java.util.UUID;

/** Password rules of the current policy (spec §8 enforcement points). */
public interface PasswordPolicy {

    /** Message keys of every broken rule (empty when acceptable); userId null for users not yet stored. */
    List<String> violations(String password, String username, String email, UUID userId);

    /** SecureRandom password satisfying the policy, at least max(minLength, 14) chars. */
    String generateTemporary();
}
