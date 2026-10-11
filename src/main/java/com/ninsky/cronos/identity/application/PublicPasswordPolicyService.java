package com.ninsky.cronos.identity.application;

import com.ninsky.cronos.iam.policy.SecurityPolicy;
import com.ninsky.cronos.iam.policy.SecurityPolicyProvider;
import com.ninsky.cronos.identity.api.PublicPasswordPolicy;
import com.ninsky.cronos.identity.infrastructure.SignInPolicyProvider;
import org.springframework.stereotype.Service;

/** Public projection of the password rules (§2.4.3); both sources are cached and evicted on policy change. */
@Service
public class PublicPasswordPolicyService {

    private final SecurityPolicyProvider policies;
    private final SignInPolicyProvider signIn;

    public PublicPasswordPolicyService(SecurityPolicyProvider policies, SignInPolicyProvider signIn) {
        this.policies = policies;
        this.signIn = signIn;
    }

    /** The rules a new password must satisfy. */
    public PublicPasswordPolicy current() {
        SecurityPolicy policy = policies.current();
        return new PublicPasswordPolicy(policy.passwordMinLength(), policy.passwordRequireUppercase(), policy.passwordRequireLowercase(),
                policy.passwordRequireDigit(), policy.passwordRequireSymbol(), signIn.current().disallowUserInfo());
    }
}
