package com.ninsky.cronos.domain.service.core;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Invariants shared by every write path into the unit catalog — the REST create/update endpoints
 * and the .xlsx bulk import both call these, so the two can never disagree on what a valid unit is.
 * Pure functions: no Spring, no persistence.
 */
public final class MeasurementUnitRules {

    /** Mirrors {@code measurement_units.multiplier_to_base numeric(20,10)}. */
    public static final int FACTOR_MAX_SCALE = 10;
    public static final int FACTOR_MAX_INTEGER_DIGITS = 10;
    public static final int CODE_MAX_LENGTH = 20;
    public static final int NAME_MAX_LENGTH = 100;

    /**
     * Letters/digits first, then letters, digits, {@code . _ -}. No spaces: codes are identifiers
     * that other modules look up literally ({@code g}, {@code cup}, {@code tbsp}, {@code tsp}).
     * Case is significant for measurement-unit codes ({@code T} tablespoon vs {@code t} teaspoon).
     */
    public static final String CODE_REGEX = "^[\\p{L}\\p{N}][\\p{L}\\p{N}._-]*$";
    private static final Pattern CODE_PATTERN = Pattern.compile(CODE_REGEX);

    /** First characters spreadsheet apps interpret as a formula (CSV/formula injection on re-export). */
    private static final String FORMULA_TRIGGERS = "=+-@";

    private MeasurementUnitRules() {
    }

    public enum Violation {
        FACTOR_REQUIRED,
        FACTOR_NOT_POSITIVE,
        FACTOR_PRECISION,
        BASE_FACTOR_MUST_BE_ONE,
        CODE_FORMAT,
        TEXT_FORMULA_INJECTION
    }

    public static Optional<Violation> checkFactor(BigDecimal factor, boolean isBaseUnit) {
        if (factor == null) {
            return Optional.of(Violation.FACTOR_REQUIRED);
        }
        if (factor.signum() <= 0) {
            return Optional.of(Violation.FACTOR_NOT_POSITIVE);
        }
        BigDecimal normalized = factor.stripTrailingZeros();
        int scale = Math.max(normalized.scale(), 0);
        int integerDigits = normalized.precision() - normalized.scale();
        if (scale > FACTOR_MAX_SCALE || integerDigits > FACTOR_MAX_INTEGER_DIGITS) {
            return Optional.of(Violation.FACTOR_PRECISION);
        }
        if (isBaseUnit && factor.compareTo(BigDecimal.ONE) != 0) {
            return Optional.of(Violation.BASE_FACTOR_MUST_BE_ONE);
        }
        return Optional.empty();
    }

    public static Optional<Violation> checkCode(String code) {
        if (code == null || code.length() > CODE_MAX_LENGTH || !CODE_PATTERN.matcher(code).matches()) {
            return Optional.of(Violation.CODE_FORMAT);
        }
        return Optional.empty();
    }

    /** Free-text catalog fields must not start with a spreadsheet formula trigger. */
    public static Optional<Violation> checkDisplayText(String text) {
        if (text != null && !text.isEmpty() && FORMULA_TRIGGERS.indexOf(text.charAt(0)) >= 0) {
            return Optional.of(Violation.TEXT_FORMULA_INJECTION);
        }
        return Optional.empty();
    }
}
