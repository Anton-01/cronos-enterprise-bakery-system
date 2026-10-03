package com.ninsky.cronos.account.profile.domain;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import com.ninsky.cronos.account.shared.domain.DomainValidationException;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Phone number in E.164 ({@code +525512345678}). Valid only if it matches the E.164 shape AND
 * libphonenumber considers it a real, dialable number for its country code.
 */
public record E164Phone(String value) {

    public static final Pattern FORMAT = Pattern.compile("^\\+[1-9]\\d{6,14}$");
    private static final PhoneNumberUtil PHONE_UTIL = PhoneNumberUtil.getInstance();

    public E164Phone {
        value = normalize(value).orElseThrow(() -> new DomainValidationException("account.profile.phoneNumber.invalid"));
    }

    public static E164Phone ofNullable(String value) {
        return value == null || value.isBlank() ? null : new E164Phone(value);
    }

    public static boolean isValid(String candidate) {
        return normalize(candidate).isPresent();
    }

    /** Canonical E.164 form, or empty when the candidate is not a valid international number. */
    public static Optional<String> normalize(String candidate) {
        if (candidate == null) {
            return Optional.empty();
        }
        String trimmed = candidate.strip();
        if (!FORMAT.matcher(trimmed).matches()) {
            return Optional.empty();
        }
        return parseValid(trimmed, null);
    }

    /**
     * Legacy data path: bare national digits ("5512345678") interpreted in {@code defaultRegion}.
     * Already-international input is accepted as-is.
     */
    public static Optional<E164Phone> fromLegacy(String raw, String defaultRegion) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return parseValid(raw.strip(), defaultRegion).map(E164Phone::new);
    }

    private static Optional<String> parseValid(String raw, String region) {
        try {
            Phonenumber.PhoneNumber parsed = PHONE_UTIL.parse(raw, region);
            if (!PHONE_UTIL.isValidNumber(parsed)) {
                return Optional.empty();
            }
            return Optional.of(PHONE_UTIL.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164));
        } catch (NumberParseException e) {
            return Optional.empty();
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
