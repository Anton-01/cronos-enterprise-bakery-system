package com.ninsky.cronos.account.fiscal.domain;

import com.ninsky.cronos.account.shared.domain.DomainValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LegalNameTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "PASTELERIA CRONOS, S.A. DE C.V.", "Pasteleria Cronos SA de CV", "CRONOS S. A. DE C. V.",
            "CRONOS S. DE R.L.", "CRONOS S DE RL DE CV", "CRONOS S.A.P.I. DE C.V.", "CRONOS SAPI",
            "CRONOS S.A.S.", "CRONOS SAS DE CV", "CRONOS, S.C.", "CRONOS A.C.", "CRONOS AC"})
    void detectsCorporateRegimeSuffixWithOrWithoutDotsAndCommas(String name) {
        assertThat(LegalName.hasCorporateSuffix(name)).isTrue();
        assertThatThrownBy(() -> new LegalName(name))
                .isInstanceOf(DomainValidationException.class)
                .hasMessage("account.fiscal.legalName.corporateSuffix");
    }

    @ParameterizedTest
    @ValueSource(strings = {"PASTELERIA CRONOS", "MAC", "ASOCIACION", "SAS PASTELES", "CAFE SACO", "PANADERIA LA SC0"})
    void doesNotFlagWordsThatMerelyContainTheLetters(String name) {
        assertThat(LegalName.hasCorporateSuffix(name)).isFalse();
    }

    @Test
    void normalizesToUpperCaseWithCollapsedWhitespace() {
        assertThat(new LegalName("  pastelería   cronos ").value()).isEqualTo("PASTELERÍA CRONOS");
    }

    @Test
    void enforcesRequiredAndMaxLength() {
        assertThatThrownBy(() -> new LegalName("   ")).hasMessage("account.fiscal.legalName.required");
        assertThat(new LegalName("X".repeat(254)).value()).hasSize(254);
        assertThatThrownBy(() -> new LegalName("X".repeat(255))).hasMessage("account.fiscal.legalName.size");
    }
}
