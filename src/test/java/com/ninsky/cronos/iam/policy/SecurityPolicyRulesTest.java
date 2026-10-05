package com.ninsky.cronos.iam.policy;

import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.infrastructure.exception.Violations;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class SecurityPolicyRulesTest {

    private static final Set<Long> ACTIVE = Set.of(1L, 2L, 3L);

    private static List<ApiException.Violation> violations(SecurityPolicyRequest request) {
        Violations violations = SecurityPolicyRules.validate(request, ACTIVE);
        try {
            violations.throwIfAny();
            return List.of();
        } catch (ApiException e) {
            return e.violations();
        }
    }

    private static SecurityPolicyRequest with(Integer minLength, Integer idle, Integer absolute, List<Long> roles, Long version) {
        return new SecurityPolicyRequest(minLength, true, true, true, true, 5, 90, 5, 15, idle, absolute, 3, 72, roles, version);
    }

    @Test
    void validRequestPasses() {
        assertThat(SecurityPolicyRules.validate(PolicyFixtures.request(), ACTIVE).isEmpty()).isTrue();
    }

    @Test
    void reportsRangesAndMissingFieldsTogether() {
        SecurityPolicyRequest request = new SecurityPolicyRequest(7, null, true, true, true, 25, 90, 5, 15, 30, 12, 3, 72,
                List.of(), null);
        assertThat(violations(request)).extracting(ApiException.Violation::field, ApiException.Violation::messageKey)
                .containsExactlyInAnyOrder(
                        tuple("passwordMinLength", "api.validation.range"),
                        tuple("passwordHistory", "api.validation.range"),
                        tuple("passwordRequireUppercase", "api.validation.required"),
                        tuple("version", "api.validation.required"));
        assertThat(violations(request).getFirst().args()).containsExactly(8, 128);
    }

    @Test
    void idleTimeoutCannotExceedTheAbsoluteLimit() {
        assertThat(violations(with(12, 90, 1, List.of(), 1L)))
                .singleElement()
                .satisfies(v -> {
                    assertThat(v.field()).isEqualTo("sessionIdleMinutes");
                    assertThat(v.messageKey()).isEqualTo("security.policy.idleExceedsAbsolute");
                    assertThat(v.args()).containsExactly(60);
                });
        assertThat(violations(with(12, 60, 1, List.of(), 1L))).isEmpty();
    }

    @Test
    void crossFieldRuleIsSkippedWhenEitherSideIsInvalid() {
        assertThat(violations(with(12, 90, null, List.of(), 1L))).extracting(ApiException.Violation::field)
                .containsExactly("sessionAbsoluteHours");
    }

    @Test
    void rolesAreCheckedPerIndex() {
        assertThat(violations(with(12, 30, 12, Arrays.asList(1L, null, 1L, 99L), 1L)))
                .extracting(ApiException.Violation::field, ApiException.Violation::messageKey)
                .containsExactly(
                        tuple("twoFactorRequiredRoleIds[1]", "api.validation.required"),
                        tuple("twoFactorRequiredRoleIds[2]", "api.validation.duplicateEntry"),
                        tuple("twoFactorRequiredRoleIds[3]", "api.validation.unknownRole"));
        assertThat(violations(with(12, 30, 12, null, 1L))).extracting(ApiException.Violation::field)
                .containsExactly("twoFactorRequiredRoleIds");
    }

    @Test
    void toPolicySortsRolesAndBumpsTheVersion() {
        SecurityPolicy policy = with(12, 30, 12, List.of(3L, 1L, 3L), 7L).toPolicy(Instant.EPOCH, null);
        assertThat(policy.twoFactorRequiredRoleIds()).containsExactly(1L, 3L);
        assertThat(policy.version()).isEqualTo(8);
    }

    @Test
    void sameOrStricterSettingsAreNotWeakening() {
        SecurityPolicy before = PolicyFixtures.policy();
        SecurityPolicy stricter = new SecurityPolicy(16, true, true, true, true, 10, 30, 3, 60, 15, 8, 1, 24,
                List.of(1L, 2L, 3L), null, null, 4);
        assertThat(SecurityPolicyRules.weakenedControls(before, before)).isEmpty();
        assertThat(SecurityPolicyRules.weakenedControls(before, stricter)).isEmpty();
    }

    @Test
    void detectsEveryWeakenedControl() {
        SecurityPolicy before = PolicyFixtures.policy();
        SecurityPolicy weaker = new SecurityPolicy(8, false, true, false, true, 0, 0, 10, 5, 60, 24, 5, 168,
                List.of(1L), null, null, 4);
        assertThat(SecurityPolicyRules.weakenedControls(before, weaker)).containsExactly(
                "passwordMinLength", "passwordRequireUppercase", "passwordRequireDigit", "passwordHistory",
                "passwordMaxAgeDays", "maxFailedAttempts", "lockoutMinutes", "sessionIdleMinutes", "sessionAbsoluteHours",
                "maxConcurrentSessions", "invitationTtlHours", "twoFactorRequiredRoleIds");
    }

    @Test
    void enablingPasswordExpiryIsNotWeakening() {
        SecurityPolicy noExpiry = new SecurityPolicy(12, true, true, true, true, 5, 0, 5, 15, 30, 12, 3, 72, List.of(1L, 2L),
                null, null, 3);
        assertThat(SecurityPolicyRules.weakenedControls(noExpiry, PolicyFixtures.policy())).isEmpty();
        assertThat(SecurityPolicyRules.weakenedControls(PolicyFixtures.policy(), noExpiry)).containsExactly("passwordMaxAgeDays");
    }

    @Test
    void validationErrorsSurfaceAsApiException() {
        assertThatThrownBy(() -> SecurityPolicyRules.validate(with(5, 30, 12, List.of(), 1L), ACTIVE).throwIfAny())
                .isInstanceOf(ApiException.class);
    }
}
