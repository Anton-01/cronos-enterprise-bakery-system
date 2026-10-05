package com.ninsky.cronos.iam.policy;

/** Current policy, cached briefly. */
public interface SecurityPolicyProvider {

    SecurityPolicy current();
}
