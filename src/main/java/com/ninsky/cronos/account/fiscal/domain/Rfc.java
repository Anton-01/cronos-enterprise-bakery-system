package com.ninsky.cronos.account.fiscal.domain;

import com.ninsky.cronos.account.shared.domain.DomainValidationException;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Registro Federal de Contribuyentes. Self-validating: format (persona física / moral), a real
 * YYMMDD date, not one of SAT's generic RFCs, and SAT's mod-11 check digit. The value is always
 * stored trimmed and upper-cased.
 */
public record Rfc(String value) {

    public static final int INDIVIDUAL_LENGTH = 13;
    public static final int LEGAL_ENTITY_LENGTH = 12;

    private static final Pattern INDIVIDUAL = Pattern.compile("^[A-ZÑ&]{4}\\d{6}[A-Z\\d]{2}[A\\d]$");
    private static final Pattern LEGAL_ENTITY = Pattern.compile("^[A-ZÑ&]{3}\\d{6}[A-Z\\d]{2}[A\\d]$");

    /** "Público en general" and "residente en el extranjero": valid for CFDI receivers, never as an account's own RFC. */
    public static final Set<String> GENERIC_RFCS = Set.of("XAXX010101000", "XEXX010101000");

    /** SAT's check-digit alphabet: the character's index is its value. */
    private static final String CHECK_DIGIT_ALPHABET = "0123456789ABCDEFGHIJKLMN&OPQRSTUVWXYZ Ñ";

    /** Why a candidate RFC is rejected, most fundamental first. */
    public enum Violation {
        REQUIRED("account.fiscal.taxId.required"),
        FORMAT("account.fiscal.taxId.format"),
        GENERIC("account.fiscal.taxId.generic"),
        INVALID_DATE("account.fiscal.taxId.date"),
        CHECK_DIGIT("account.fiscal.taxId.checkDigit");

        private final String messageKey;

        Violation(String messageKey) {
            this.messageKey = messageKey;
        }

        public String messageKey() {
            return messageKey;
        }
    }

    public Rfc {
        value = normalize(value);
        Optional<Violation> violation = check(value);
        if (violation.isPresent()) {
            throw new DomainValidationException(violation.get().messageKey());
        }
    }

    /** Parses and classifies in one step. */
    public static TaxpayerIdentity parse(String raw) {
        return new Rfc(raw).identity();
    }

    public TaxpayerIdentity identity() {
        return value.length() == INDIVIDUAL_LENGTH
                ? new TaxpayerIdentity.Individual(this)
                : new TaxpayerIdentity.LegalEntity(this);
    }

    public TaxpayerType taxpayerType() {
        return identity().type();
    }

    public static String normalize(String raw) {
        return raw == null ? null : raw.strip().toUpperCase(Locale.ROOT);
    }

    /** Non-throwing validation for Bean Validation / rule reuse. Expects an already-normalized value. */
    public static Optional<Violation> check(String candidate) {
        if (candidate == null || candidate.isEmpty()) {
            return Optional.of(Violation.REQUIRED);
        }
        boolean individual = INDIVIDUAL.matcher(candidate).matches();
        if (!individual && !LEGAL_ENTITY.matcher(candidate).matches()) {
            return Optional.of(Violation.FORMAT);
        }
        if (GENERIC_RFCS.contains(candidate)) {
            return Optional.of(Violation.GENERIC);
        }
        int dateStart = individual ? 4 : 3;
        if (!isRealDate(candidate.substring(dateStart, dateStart + 6))) {
            return Optional.of(Violation.INVALID_DATE);
        }
        if (computeCheckDigit(candidate) != candidate.charAt(candidate.length() - 1)) {
            return Optional.of(Violation.CHECK_DIGIT);
        }
        return Optional.empty();
    }

    /**
     * SAT mod-11: a 12-char (persona moral) RFC is left-padded with a space to 13; the first 12
     * characters are weighted 13..2, summed, and {@code 11 - (sum % 11)} gives the digit
     * (11 → '0', 10 → 'A').
     */
    static char computeCheckDigit(String rfc) {
        String padded = rfc.length() == LEGAL_ENTITY_LENGTH ? " " + rfc : rfc;
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int charValue = CHECK_DIGIT_ALPHABET.indexOf(padded.charAt(i));
            if (charValue < 0) {
                return '?';
            }
            sum += charValue * (13 - i);
        }
        int remainder = sum % 11;
        if (remainder == 0) {
            return '0';
        }
        int digit = 11 - remainder;
        return digit == 10 ? 'A' : Character.forDigit(digit, 10);
    }

    /** YYMMDD is century-ambiguous: accept it if it's a real date in either 19YY or 20YY (e.g. 000229). */
    private static boolean isRealDate(String yymmdd) {
        int yy = Integer.parseInt(yymmdd.substring(0, 2));
        int mm = Integer.parseInt(yymmdd.substring(2, 4));
        int dd = Integer.parseInt(yymmdd.substring(4, 6));
        return isValidDate(1900 + yy, mm, dd) || isValidDate(2000 + yy, mm, dd);
    }

    private static boolean isValidDate(int year, int month, int day) {
        try {
            LocalDate.of(year, month, day);
            return true;
        } catch (DateTimeException e) {
            return false;
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
