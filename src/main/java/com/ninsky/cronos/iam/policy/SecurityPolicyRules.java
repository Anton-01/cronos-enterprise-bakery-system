package com.ninsky.cronos.iam.policy;

import com.ninsky.cronos.infrastructure.exception.Violations;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.IntStream;

/** Pure validation and weakening detection for the security policy (spec §8). */
public final class SecurityPolicyRules {

    private record Range(String field, Function<SecurityPolicyRequest, Integer> value, int min, int max) {
    }

    private record Control(String field, BiPredicate<SecurityPolicy, SecurityPolicy> weakened) {
    }

    private static final List<Range> RANGES = List.of(
            new Range("passwordMinLength", SecurityPolicyRequest::passwordMinLength, 8, 128),
            new Range("passwordHistory", SecurityPolicyRequest::passwordHistory, 0, 24),
            new Range("passwordMaxAgeDays", SecurityPolicyRequest::passwordMaxAgeDays, 0, 365),
            new Range("maxFailedAttempts", SecurityPolicyRequest::maxFailedAttempts, 3, 20),
            new Range("lockoutMinutes", SecurityPolicyRequest::lockoutMinutes, 1, 1440),
            new Range("sessionIdleMinutes", SecurityPolicyRequest::sessionIdleMinutes, 5, 480),
            new Range("sessionAbsoluteHours", SecurityPolicyRequest::sessionAbsoluteHours, 1, 720),
            new Range("maxConcurrentSessions", SecurityPolicyRequest::maxConcurrentSessions, 1, 20),
            new Range("invitationTtlHours", SecurityPolicyRequest::invitationTtlHours, 1, 336));

    private static final Map<String, Function<SecurityPolicyRequest, Boolean>> FLAGS = orderedFlags();

    private static final List<Control> CONTROLS = List.of(
            lower("passwordMinLength", SecurityPolicy::passwordMinLength),
            turnedOff("passwordRequireUppercase", SecurityPolicy::passwordRequireUppercase),
            turnedOff("passwordRequireLowercase", SecurityPolicy::passwordRequireLowercase),
            turnedOff("passwordRequireDigit", SecurityPolicy::passwordRequireDigit),
            turnedOff("passwordRequireSymbol", SecurityPolicy::passwordRequireSymbol),
            lower("passwordHistory", SecurityPolicy::passwordHistory),
            new Control("passwordMaxAgeDays", (b, a) -> b.passwordMaxAgeDays() > 0
                    && (a.passwordMaxAgeDays() == 0 || a.passwordMaxAgeDays() > b.passwordMaxAgeDays())),
            higher("maxFailedAttempts", SecurityPolicy::maxFailedAttempts),
            lower("lockoutMinutes", SecurityPolicy::lockoutMinutes),
            higher("sessionIdleMinutes", SecurityPolicy::sessionIdleMinutes),
            higher("sessionAbsoluteHours", SecurityPolicy::sessionAbsoluteHours),
            higher("maxConcurrentSessions", SecurityPolicy::maxConcurrentSessions),
            higher("invitationTtlHours", SecurityPolicy::invitationTtlHours),
            new Control("twoFactorRequiredRoleIds",
                    (b, a) -> !Set.copyOf(a.twoFactorRequiredRoleIds()).containsAll(b.twoFactorRequiredRoleIds())));

    private SecurityPolicyRules() {
    }

    /**
     * Every violation at once; {@code activeRoleIds} are the requested ids that exist and are ACTIVE,
     * {@code superAdminRoleId} is never allowed (break-glass rule, contract §8.3).
     */
    public static Violations validate(SecurityPolicyRequest request, Set<Long> activeRoleIds, Long superAdminRoleId) {
        Violations violations = new Violations();
        RANGES.forEach(range -> {
            Integer value = range.value().apply(request);
            violations.invalidIf(value == null, range.field(), "api.validation.required")
                    .invalidIf(value != null && (value < range.min() || value > range.max()),
                            range.field(), "api.validation.range", range.min(), range.max());
        });
        FLAGS.forEach((field, flag) -> violations.invalidIf(flag.apply(request) == null, field, "api.validation.required"));
        if (!violations.hasField("sessionIdleMinutes") && !violations.hasField("sessionAbsoluteHours")
                && request.sessionIdleMinutes() > request.sessionAbsoluteHours() * 60) {
            violations.invalid("sessionIdleMinutes", "security.policy.idleExceedsAbsolute", request.sessionAbsoluteHours() * 60);
        }
        validateRoles(request.twoFactorRequiredRoleIds(), activeRoleIds, superAdminRoleId, violations);
        violations.invalidIf(request.version() == null, "version", "api.validation.required");
        return violations;
    }

    /** Fields whose new value is less strict than before; any → WARNING severity. */
    public static List<String> weakenedControls(SecurityPolicy before, SecurityPolicy after) {
        return CONTROLS.stream().filter(c -> c.weakened().test(before, after)).map(Control::field).toList();
    }

    /** Field → value view used for audit diffs. */
    public static Map<String, Object> snapshot(SecurityPolicy p) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("passwordMinLength", p.passwordMinLength());
        values.put("passwordRequireUppercase", p.passwordRequireUppercase());
        values.put("passwordRequireLowercase", p.passwordRequireLowercase());
        values.put("passwordRequireDigit", p.passwordRequireDigit());
        values.put("passwordRequireSymbol", p.passwordRequireSymbol());
        values.put("passwordHistory", p.passwordHistory());
        values.put("passwordMaxAgeDays", p.passwordMaxAgeDays());
        values.put("maxFailedAttempts", p.maxFailedAttempts());
        values.put("lockoutMinutes", p.lockoutMinutes());
        values.put("sessionIdleMinutes", p.sessionIdleMinutes());
        values.put("sessionAbsoluteHours", p.sessionAbsoluteHours());
        values.put("maxConcurrentSessions", p.maxConcurrentSessions());
        values.put("invitationTtlHours", p.invitationTtlHours());
        values.put("twoFactorRequiredRoleIds", p.twoFactorRequiredRoleIds().stream().sorted().toList());
        return values;
    }

    private static void validateRoles(List<Long> roleIds, Set<Long> activeRoleIds, Long superAdminRoleId, Violations violations) {
        if (roleIds == null) {
            violations.invalid("twoFactorRequiredRoleIds", "api.validation.required");
            return;
        }
        Set<Long> seen = new HashSet<>();
        IntStream.range(0, roleIds.size()).forEach(i -> {
            Long id = roleIds.get(i);
            String field = "twoFactorRequiredRoleIds[" + i + "]";
            if (id == null) {
                violations.invalid(field, "api.validation.required");
            } else if (id.equals(superAdminRoleId)) {
                violations.invalid(field, "security.policy.superAdminTwoFactorForbidden");
            } else if (!seen.add(id)) {
                violations.invalid(field, "api.validation.duplicateEntry");
            } else if (!activeRoleIds.contains(id)) {
                violations.invalid(field, "api.validation.unknownRole", id);
            }
        });
    }

    private static Map<String, Function<SecurityPolicyRequest, Boolean>> orderedFlags() {
        Map<String, Function<SecurityPolicyRequest, Boolean>> flags = new LinkedHashMap<>();
        flags.put("passwordRequireUppercase", SecurityPolicyRequest::passwordRequireUppercase);
        flags.put("passwordRequireLowercase", SecurityPolicyRequest::passwordRequireLowercase);
        flags.put("passwordRequireDigit", SecurityPolicyRequest::passwordRequireDigit);
        flags.put("passwordRequireSymbol", SecurityPolicyRequest::passwordRequireSymbol);
        return flags;
    }

    private static Control lower(String field, ToIntFunction<SecurityPolicy> value) {
        return new Control(field, (b, a) -> value.applyAsInt(a) < value.applyAsInt(b));
    }

    private static Control higher(String field, ToIntFunction<SecurityPolicy> value) {
        return new Control(field, (b, a) -> value.applyAsInt(a) > value.applyAsInt(b));
    }

    private static Control turnedOff(String field, java.util.function.Predicate<SecurityPolicy> flag) {
        return new Control(field, (b, a) -> flag.test(b) && !flag.test(a));
    }
}
