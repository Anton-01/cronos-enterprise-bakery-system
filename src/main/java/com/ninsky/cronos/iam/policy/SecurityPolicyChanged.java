package com.ninsky.cronos.iam.policy;

/** Published inside the transaction that updated the policy; caches drop it after commit. */
public record SecurityPolicyChanged(long version) {
}
