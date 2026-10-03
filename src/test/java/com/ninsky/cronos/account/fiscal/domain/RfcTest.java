package com.ninsky.cronos.account.fiscal.domain;

import com.ninsky.cronos.account.shared.domain.DomainValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RfcTest {

    @ParameterizedTest(name = "{0} is a valid {1} RFC")
    @CsvSource({
            "GODE561231GR8, INDIVIDUAL",
            "gode561231gr8, INDIVIDUAL",
            "' GODE561231GR8 ', INDIVIDUAL",
            "GODE000229GR4, INDIVIDUAL",    // 29-Feb: valid in 2000
            "ÑAND010101A19, INDIVIDUAL",    // Ñ in the name prefix
            "CRO200101AB2, LEGAL_ENTITY",
            "&MX010101A17, LEGAL_ENTITY"    // & in the company prefix
    })
    void acceptsValidRfcs(String raw, TaxpayerType expectedType) {
        Rfc rfc = new Rfc(raw);

        assertThat(rfc.value()).isEqualTo(raw.strip().toUpperCase(java.util.Locale.ROOT));
        assertThat(rfc.taxpayerType()).isEqualTo(expectedType);
        assertThat(Rfc.parse(raw).type()).isEqualTo(expectedType);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "'', REQUIRED",
            "GOD561231GR8X, FORMAT",
            "GODE561231GR, FORMAT",          // 12 chars but 4-letter prefix
            "GODE561231GR8A, FORMAT",        // 14 chars
            "G0DE561231GR8, FORMAT",         // digit in name prefix
            "GODE561231GRB, FORMAT",         // check digit must be [A0-9]
            "XAXX010101000, GENERIC",
            "XEXX010101000, GENERIC",
            "GODE561331GR8, INVALID_DATE",   // month 13
            "GODE560230GR8, INVALID_DATE",   // 30-Feb
            "GODE561200GR8, INVALID_DATE",   // day 00
            "GODE561231GR9, CHECK_DIGIT",
            "CRO200101AB3, CHECK_DIGIT"
    })
    void rejectsInvalidRfcsWithTheMostSpecificReason(String raw, Rfc.Violation expected) {
        assertThat(Rfc.check(Rfc.normalize(raw))).contains(expected);
        assertThatThrownBy(() -> new Rfc(raw))
                .isInstanceOf(DomainValidationException.class)
                .extracting(e -> ((DomainValidationException) e).messageKey())
                .isEqualTo(expected.messageKey());
    }

    @Test
    void checkDigitFollowsSatMod11() {
        // GODE561231GR: 16*13 + 25*12 + 13*11 + 14*10 + 5*9 + 6*8 + 1*7 + 2*6 + 3*5 + 1*4 + 16*3 + 28*2 = 1026
        // 1026 % 11 = 3 -> 11 - 3 = 8
        assertThat(Rfc.computeCheckDigit("GODE561231GR0")).isEqualTo('8');
    }

    @ParameterizedTest
    @ValueSource(strings = {"GODE561231GR8", "CRO200101AB2"})
    void computedCheckDigitMatchesValidRfcs(String rfc) {
        assertThat(Rfc.computeCheckDigit(rfc)).isEqualTo(rfc.charAt(rfc.length() - 1));
    }

    @Test
    void identityIsSealedAndExhaustive() {
        String kind = switch (Rfc.parse("CRO200101AB2")) {
            case TaxpayerIdentity.Individual i -> "PF";
            case TaxpayerIdentity.LegalEntity l -> "PM";
        };
        assertThat(kind).isEqualTo("PM");
    }
}
