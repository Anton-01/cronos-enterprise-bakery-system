package com.ninsky.cronos.account.fiscal.domain;

import com.ninsky.cronos.account.shared.domain.DomainValidationException;

import java.util.regex.Pattern;

/** Mexican código postal: five digits, first two a valid postal-zone prefix (01–99; "00" does not exist). */
public record MxZipCode(String value) {

    private static final Pattern FORMAT = Pattern.compile("^(0[1-9]|[1-9]\\d)\\d{3}$");

    public MxZipCode {
        value = value == null ? null : value.strip();
        if (!isValid(value)) {
            throw new DomainValidationException("account.fiscal.zipCode.format");
        }
    }

    public static boolean isValid(String candidate) {
        return candidate != null && FORMAT.matcher(candidate.strip()).matches();
    }

    @Override
    public String toString() {
        return value;
    }
}
