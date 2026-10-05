package com.ninsky.cronos.iam.policy;

import com.ninsky.cronos.infrastructure.exception.Violations;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntPredicate;

/** Message keys of the password rules and the pure checks behind them (spec §8). */
public final class PasswordRules {

    public static final String REQUIRED = "security.password.required";
    public static final String MIN_LENGTH = "security.password.minLength";
    public static final String MAX_BYTES = "security.password.maxLength";
    public static final String UPPERCASE = "security.password.uppercase";
    public static final String LOWERCASE = "security.password.lowercase";
    public static final String DIGIT = "security.password.digit";
    public static final String SYMBOL = "security.password.symbol";
    public static final String CONTAINS_USERNAME = "security.password.containsUsername";
    public static final String CONTAINS_EMAIL = "security.password.containsEmail";
    public static final String COMMON = "security.password.common";
    public static final String REUSED = "security.password.reused";

    /** BCrypt only hashes the first 72 bytes; longer input is rejected by the encoder. */
    public static final int MAX_UTF8_BYTES = 72;
    private static final int MIN_IDENTITY_FRAGMENT = 3;

    private PasswordRules() {
    }

    /** Every rule that does not need stored hashes. */
    public static List<String> check(String password, String username, String email, SecurityPolicy policy, CommonPasswords common) {
        if (password == null || password.isEmpty()) {
            return List.of(REQUIRED);
        }
        List<String> keys = new ArrayList<>();
        addIf(keys, password.codePointCount(0, password.length()) < policy.passwordMinLength(), MIN_LENGTH);
        addIf(keys, password.getBytes(StandardCharsets.UTF_8).length > MAX_UTF8_BYTES, MAX_BYTES);
        addIf(keys, policy.passwordRequireUppercase() && none(password, Character::isUpperCase), UPPERCASE);
        addIf(keys, policy.passwordRequireLowercase() && none(password, Character::isLowerCase), LOWERCASE);
        addIf(keys, policy.passwordRequireDigit() && none(password, Character::isDigit), DIGIT);
        addIf(keys, policy.passwordRequireSymbol() && none(password, PasswordRules::isSymbol), SYMBOL);
        addIf(keys, containsFragment(password, username), CONTAINS_USERNAME);
        addIf(keys, containsFragment(password, emailLocalPart(email)), CONTAINS_EMAIL);
        addIf(keys, common.matches(password), COMMON);
        return keys;
    }

    /** Adds one violation per key on {@code field}, with the arguments each message needs. */
    public static Violations toViolations(Violations violations, String field, List<String> keys, SecurityPolicy policy) {
        keys.forEach(key -> violations.invalid(field, key, args(key, policy)));
        return violations;
    }

    public static Object[] args(String key, SecurityPolicy policy) {
        return switch (key) {
            case MIN_LENGTH -> new Object[]{policy.passwordMinLength()};
            case MAX_BYTES -> new Object[]{MAX_UTF8_BYTES};
            case REUSED -> new Object[]{policy.passwordHistory()};
            default -> new Object[0];
        };
    }

    static boolean isSymbol(int c) {
        return !Character.isLetterOrDigit(c) && !Character.isWhitespace(c);
    }

    static String emailLocalPart(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        return at < 0 ? email : email.substring(0, at);
    }

    private static boolean containsFragment(String password, String fragment) {
        return fragment != null && fragment.length() >= MIN_IDENTITY_FRAGMENT
                && password.toLowerCase(Locale.ROOT).contains(fragment.toLowerCase(Locale.ROOT));
    }

    private static boolean none(String value, IntPredicate predicate) {
        return value.codePoints().noneMatch(predicate);
    }

    private static void addIf(List<String> keys, boolean condition, String key) {
        if (condition) {
            keys.add(key);
        }
    }
}
