package com.ninsky.cronos.finance.currency;

import com.ninsky.cronos.finance.shared.FieldIssue;

import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Pure ISO 4217 checks (spec §9.2) against the JDK's currency table. */
public final class CurrencyRules {

    static final Pattern CODE = Pattern.compile("^[A-Z]{3}$");
    static final Pattern NUMERIC = Pattern.compile("^\\d{3}$");
    private static final Set<String> ISO_CODES = Currency.getAvailableCurrencies().stream()
            .map(Currency::getCurrencyCode)
            .collect(Collectors.toUnmodifiableSet());

    private CurrencyRules() {
    }

    /** Issues for a well-formed code unknown to ISO 4217, or a numeric code that does not match it. */
    public static List<FieldIssue> check(String code, String numericCode) {
        if (code == null || !CODE.matcher(code).matches()) {
            return List.of();
        }
        if (!ISO_CODES.contains(code)) {
            return List.of(new FieldIssue("code", "finance.currency.code.notIso"));
        }
        return isoNumeric(code)
                .filter(expected -> numericCode != null && NUMERIC.matcher(numericCode).matches() && !expected.equals(numericCode))
                .map(expected -> List.of(new FieldIssue("numericCode", "finance.currency.numericCode.mismatch")))
                .orElse(List.of());
    }

    /** The ISO numeric code, when the JDK knows it. */
    public static Optional<String> isoNumeric(String code) {
        return Optional.of(Currency.getInstance(code).getNumericCode())
                .filter(numeric -> numeric > 0)
                .map(numeric -> String.format("%03d", numeric));
    }
}
