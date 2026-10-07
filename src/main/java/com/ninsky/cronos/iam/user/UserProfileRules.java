package com.ninsky.cronos.iam.user;

import com.ninsky.cronos.account.profile.domain.E164Phone;
import com.ninsky.cronos.iam.shared.IamRules;
import com.ninsky.cronos.iam.shared.TenantTime;
import com.ninsky.cronos.iam.shared.Texts;
import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.Violations;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Field rules of create and update (spec §3.2); violations are collected, uniqueness included. */
@Component
@RequiredArgsConstructor
public class UserProfileRules {

    public static final Pattern USERNAME = Pattern.compile("^[a-zA-Z0-9](?:[a-zA-Z0-9._-]{1,48})[a-zA-Z0-9]$");
    private static final Pattern CONSECUTIVE_SEPARATORS = Pattern.compile("[._-]{2}");
    private static final Pattern NAME = Pattern.compile("^[\\p{L}\\p{M} .'-]+$");
    private static final Pattern EMPLOYEE_NUMBER = Pattern.compile("^[A-Za-z0-9-]+$");
    private static final Pattern EMAIL = Pattern.compile(
            "^[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*@(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$");
    private static final Set<String> RESERVED = Set.of("admin", "root", "system", "support", "null", "undefined");
    private static final Set<String> LOCALES = Set.of("es-MX", "en");
    private static final int NAME_MAX = 100;
    private static final int TEXT_MAX = 100;
    private static final int EMAIL_MAX = 254;
    private static final int EMPLOYEE_NUMBER_MAX = 30;

    private final UserReadCustomRepository users;
    private final Clock clock;

    /** Raw input of either request. */
    public record Input(String username, String email, String firstName, String lastName, String phoneNumber,
                        String jobTitle, String department, String employeeNumber, String locale,
                        LocalDate accessExpiresAt, Boolean requireTwoFactor) {
    }

    /**
     * @param existing current row on update (null on create); {@code accessExpiresAt} is only
     *                 checked against today when it changed
     */
    public UserProfile validate(Violations violations, Input in, UserRow existing) {
        UUID excludeId = existing == null ? null : existing.id();
        String username = username(violations, in.username(), excludeId);
        String email = email(violations, in.email(), excludeId);
        String firstName = name(violations, "firstName", in.firstName());
        String lastName = name(violations, "lastName", in.lastName());
        String phone = phone(violations, in.phoneNumber());
        String jobTitle = IamRules.optionalText(violations, "jobTitle", in.jobTitle(), TEXT_MAX);
        String department = IamRules.optionalText(violations, "department", in.department(), TEXT_MAX);
        String employeeNumber = employeeNumber(violations, in.employeeNumber(), excludeId);
        String locale = Texts.clean(in.locale());
        violations.invalidIf(locale == null || !LOCALES.contains(locale), "locale", "iam.validation.locale");
        LocalDate expires = in.accessExpiresAt();
        boolean expiryChanged = existing == null || !Objects.equals(existing.accessExpiresAt(), expires);
        violations.invalidIf(expires != null && expiryChanged && !expires.isAfter(TenantTime.today(clock)),
                "accessExpiresAt", "iam.validation.futureDate");
        return new UserProfile(username, email, firstName, lastName, phone, jobTitle, department, employeeNumber,
                locale, expires, Boolean.TRUE.equals(in.requireTwoFactor()));
    }

    /** Trimmed username, or null when malformed (availability check). */
    public static String normalizeUsername(String raw) {
        String value = Texts.clean(raw);
        return value != null && USERNAME.matcher(value).matches() ? value : null;
    }

    /** Trimmed lowercase email, or null when malformed. */
    public static String normalizeEmail(String raw) {
        String value = Texts.clean(raw);
        if (value == null) {
            return null;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.length() <= EMAIL_MAX && EMAIL.matcher(lower).matches() ? lower : null;
    }

    private String username(Violations violations, String raw, UUID excludeId) {
        String value = Texts.clean(raw);
        if (value == null) {
            violations.invalid("username", "api.validation.required");
        } else if (value.length() < 3 || value.length() > 50 || !USERNAME.matcher(value).matches()
                || CONSECUTIVE_SEPARATORS.matcher(value).find()) {
            violations.invalid("username", "iam.validation.username");
        } else if (RESERVED.contains(value.toLowerCase(Locale.ROOT))) {
            violations.invalid("username", "iam.validation.usernameReserved");
        } else if (users.usernameTaken(value, excludeId)) {
            violations.add(ApiErrorCode.DUPLICATE_RESOURCE, "username", "iam.user.usernameTaken");
        }
        return value;
    }

    private String email(Violations violations, String raw, UUID excludeId) {
        if (Texts.clean(raw) == null) {
            violations.invalid("email", "api.validation.required");
            return null;
        }
        String value = normalizeEmail(raw);
        if (value == null) {
            violations.invalid("email", "iam.validation.email");
        } else if (users.emailTaken(value, excludeId)) {
            violations.add(ApiErrorCode.DUPLICATE_RESOURCE, "email", "iam.user.emailTaken");
        }
        return value;
    }

    private static String name(Violations violations, String field, String raw) {
        String value = IamRules.requiredText(violations, field, raw, NAME_MAX);
        violations.invalidIf(value != null && !NAME.matcher(value).matches(), field, "iam.validation.personName");
        return value;
    }

    private static String phone(Violations violations, String raw) {
        String value = Texts.clean(raw);
        if (value == null) {
            return null;
        }
        return E164Phone.normalize(value).orElseGet(() -> {
            violations.invalid("phoneNumber", "iam.validation.phone");
            return value;
        });
    }

    private String employeeNumber(Violations violations, String raw, UUID excludeId) {
        String value = Texts.clean(raw);
        if (value == null) {
            return null;
        }
        if (value.length() > EMPLOYEE_NUMBER_MAX || !EMPLOYEE_NUMBER.matcher(value).matches()) {
            violations.invalid("employeeNumber", "iam.validation.employeeNumber");
        } else if (users.employeeNumberTaken(value, excludeId)) {
            violations.add(ApiErrorCode.DUPLICATE_RESOURCE, "employeeNumber", "iam.user.employeeNumberTaken");
        }
        return value;
    }
}
