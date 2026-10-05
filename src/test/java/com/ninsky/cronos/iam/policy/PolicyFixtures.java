package com.ninsky.cronos.iam.policy;

import java.time.Instant;
import java.util.List;

/** Policy and request fixtures shared by the policy tests. */
public final class PolicyFixtures {

    private PolicyFixtures() {
    }

    public static SecurityPolicy policy() {
        return new SecurityPolicy(12, true, true, true, true, 5, 90, 5, 15, 30, 12, 3, 72, List.of(1L, 2L),
                Instant.parse("2026-01-01T00:00:00Z"), null, 3);
    }

    public static SecurityPolicyRequest request() {
        return new SecurityPolicyRequest(12, true, true, true, true, 5, 90, 5, 15, 30, 12, 3, 72, List.of(1L, 2L), 3L);
    }
}
