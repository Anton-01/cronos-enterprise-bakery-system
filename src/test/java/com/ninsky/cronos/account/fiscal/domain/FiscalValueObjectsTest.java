package com.ninsky.cronos.account.fiscal.domain;

import com.ninsky.cronos.account.shared.domain.DomainValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FiscalValueObjectsTest {

    @ParameterizedTest(name = "zip {0} valid={1}")
    @CsvSource({"06600, true", "01000, true", "99999, true", "' 06600 ', true",
            "00600, false", "6600, false", "066000, false", "0660A, false", "'', false"})
    void mxZipCode(String candidate, boolean valid) {
        assertThat(MxZipCode.isValid(candidate)).isEqualTo(valid);
        if (!valid) {
            assertThatThrownBy(() -> new MxZipCode(candidate)).isInstanceOf(DomainValidationException.class);
        }
    }

    @ParameterizedTest(name = "state {0} valid={1}")
    @CsvSource({"CMX, true", "cmx, true", "JAL, true", "ZAC, true", "DIF, false", "MX-CMX, false", "XX, false"})
    void mexicanState(String code, boolean valid) {
        assertThat(MexicanState.fromCode(code).isPresent()).isEqualTo(valid);
    }

    @Test
    void thirtyTwoStates() {
        assertThat(MexicanState.values()).hasSize(32);
    }

    @Test
    void addressTrimsBlankOptionalToNullAndOnlyAcceptsMexico() {
        FiscalAddress address = new FiscalAddress(" Av. Reforma ", "222", "  ", "Juárez", "Cuauhtémoc",
                MexicanState.CMX, new MxZipCode("06600"), null);

        assertThat(address.street()).isEqualTo("Av. Reforma");
        assertThat(address.interiorNumber()).isNull();
        assertThat(address.country()).isEqualTo("MEX");
        assertThatThrownBy(() -> new FiscalAddress("a", "1", null, "b", "c", MexicanState.CMX, new MxZipCode("06600"), "USA"))
                .hasMessage("account.fiscal.address.country");
        assertThatThrownBy(() -> new FiscalAddress("a".repeat(151), "1", null, "b", "c", MexicanState.CMX, new MxZipCode("06600"), "MEX"))
                .hasMessage("account.validation.maxLength");
    }
}
