package com.ninsky.cronos.account.fiscal.domain;

import com.ninsky.cronos.account.shared.domain.DomainValidationException;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Nombre / razón social exactly as registered with SAT: upper-cased, whitespace-collapsed, and —
 * per CFDI 4.0 — WITHOUT the corporate regime suffix ("S.A. DE C.V.", "S. DE R.L.", ...).
 */
public record LegalName(String value) {

    public static final int MAX_LENGTH = 254;

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern PUNCTUATION = Pattern.compile("[.,]");
    /** "S A DE C V" -> "SA DE CV": re-joins single letters left behind once dots are stripped. */
    private static final Pattern SPACED_INITIALS = Pattern.compile("\\b(\\p{Lu}) (?=\\p{Lu}\\b)");

    /** Matched against the punctuation-free, initial-joined form (see {@link #suffixProbe}). Most specific first. */
    public static final List<Pattern> CORPORATE_SUFFIXES = List.copyOf(List.of(
            suffix("S DE RL DE CV"),
            suffix("S DE RL"),
            suffix("SAPI DE CV"),
            suffix("SAPI"),
            suffix("SAS DE CV"),
            suffix("SAS"),
            suffix("SA DE CV"),
            suffix("SA"),
            suffix("SC"),
            suffix("AC")
    ));

    public LegalName {
        value = normalize(value);
        if (value == null || value.isEmpty()) {
            throw new DomainValidationException("account.fiscal.legalName.required");
        }
        if (value.length() > MAX_LENGTH) {
            throw new DomainValidationException("account.fiscal.legalName.size", MAX_LENGTH);
        }
        if (hasCorporateSuffix(value)) {
            throw new DomainValidationException("account.fiscal.legalName.corporateSuffix");
        }
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        return WHITESPACE.matcher(raw.strip()).replaceAll(" ").toUpperCase(Locale.ROOT);
    }

    public static boolean hasCorporateSuffix(String candidate) {
        if (candidate == null) {
            return false;
        }
        String probe = suffixProbe(candidate);
        return CORPORATE_SUFFIXES.stream().anyMatch(p -> p.matcher(probe).find());
    }

    static String suffixProbe(String candidate) {
        String upper = normalize(candidate);
        String noPunctuation = WHITESPACE.matcher(PUNCTUATION.matcher(upper).replaceAll(" ")).replaceAll(" ").strip();
        return SPACED_INITIALS.matcher(noPunctuation).replaceAll("$1");
    }

    private static Pattern suffix(String collapsedForm) {
        return Pattern.compile("(?:^|\\s)" + Pattern.quote(collapsedForm) + "$");
    }

    @Override
    public String toString() {
        return value;
    }
}
