package com.ninsky.cronos.identity.application;

import com.ninsky.cronos.iam.policy.PasswordPolicy;
import com.ninsky.cronos.iam.policy.PasswordRules;
import com.ninsky.cronos.iam.policy.SecurityPolicy;
import com.ninsky.cronos.iam.policy.SecurityPolicyProvider;
import com.ninsky.cronos.identity.infrastructure.SignInPolicyProvider;
import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Enforces the password policy with the §2.9 error shape: {@code PASSWORD_POLICY_VIOLATION} (one error per broken rule,
 * {@code details.failedRules} on the first) or {@code PASSWORD_REUSED} ({@code details.historySize}).
 */
@Component
public class PasswordPolicyGuard {

    private static final Map<String, String> RULE_NAMES = Map.of(
            PasswordRules.REQUIRED, "minLength",
            PasswordRules.MIN_LENGTH, "minLength",
            PasswordRules.MAX_BYTES, "maxLength",
            PasswordRules.UPPERCASE, "uppercase",
            PasswordRules.LOWERCASE, "lowercase",
            PasswordRules.DIGIT, "digit",
            PasswordRules.SYMBOL, "symbol",
            PasswordRules.CONTAINS_USERNAME, "userInfo",
            PasswordRules.CONTAINS_EMAIL, "userInfo",
            PasswordRules.COMMON, "breached");
    private static final Set<String> USER_INFO = Set.of(PasswordRules.CONTAINS_USERNAME, PasswordRules.CONTAINS_EMAIL);

    private final PasswordPolicy passwordPolicy;
    private final SecurityPolicyProvider policies;
    private final SignInPolicyProvider signInPolicies;

    public PasswordPolicyGuard(PasswordPolicy passwordPolicy, SecurityPolicyProvider policies, SignInPolicyProvider signInPolicies) {
        this.passwordPolicy = passwordPolicy;
        this.policies = policies;
        this.signInPolicies = signInPolicies;
    }

    /**
     * @param userId null for an account not stored yet (no history to compare)
     * @throws ApiException PASSWORD_POLICY_VIOLATION or PASSWORD_REUSED on {@code field}
     */
    public void require(String field, String password, String username, String email, UUID userId) {
        boolean userInfo = signInPolicies.current().disallowUserInfo();
        List<String> keys = passwordPolicy.violations(password, username, email, userId).stream()
                .filter(key -> userInfo || !USER_INFO.contains(key))
                .toList();
        if (keys.isEmpty()) {
            return;
        }
        SecurityPolicy policy = policies.current();
        if (keys.contains(PasswordRules.REUSED)) {
            throw ApiException.withDetails(ApiErrorCode.PASSWORD_REUSED, field, Map.of("historySize", policy.passwordHistory()),
                    PasswordRules.REUSED, PasswordRules.args(PasswordRules.REUSED, policy));
        }
        List<String> failedRules = failedRules(keys);
        List<ApiException.Violation> violations = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            violations.add(new ApiException.Violation(ApiErrorCode.PASSWORD_POLICY_VIOLATION, field, key,
                    List.of(PasswordRules.args(key, policy)), i == 0 ? Map.of("failedRules", failedRules) : null));
        }
        throw new ApiException(violations, null);
    }

    /** Contract names of the broken rules, distinct, in rule order. */
    public static List<String> failedRules(List<String> keys) {
        return keys.stream().map(key -> RULE_NAMES.getOrDefault(key, "policy")).distinct().toList();
    }
}
