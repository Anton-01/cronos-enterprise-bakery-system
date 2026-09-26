package com.ninsky.cronos.account.fiscal.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaxRegimeTest {

    private static final Set<String> INDIVIDUAL_ONLY = Set.of("605", "606", "607", "608", "611", "612", "614", "615", "616", "621", "625");
    private static final Set<String> LEGAL_ENTITY_ONLY = Set.of("601", "603", "620", "622", "623", "624");
    private static final Set<String> BOTH = Set.of("610", "626");

    /** All 19 regimes x 2 taxpayer types = 38 cells of the SAT applicability matrix. */
    static Stream<Arguments> matrix() {
        return Arrays.stream(TaxRegime.values()).flatMap(regime -> Arrays.stream(TaxpayerType.values()).map(type -> {
            boolean expected = BOTH.contains(regime.code())
                    || (type == TaxpayerType.INDIVIDUAL ? INDIVIDUAL_ONLY : LEGAL_ENTITY_ONLY).contains(regime.code());
            return Arguments.of(regime.code(), type, expected);
        }));
    }

    @ParameterizedTest(name = "{0} applicable to {1}: {2}")
    @MethodSource("matrix")
    void applicabilityMatrix(String code, TaxpayerType type, boolean expected) {
        assertThat(TaxRegime.fromCode(code).orElseThrow().applicableTo(type)).isEqualTo(expected);
    }

    @Test
    void catalogHasExactlyTheNineteenCfdi40Regimes() {
        assertThat(matrix()).hasSize(38);
        assertThat(Arrays.stream(TaxRegime.values()).map(TaxRegime::code))
                .containsExactlyInAnyOrderElementsOf(Stream.of(INDIVIDUAL_ONLY, LEGAL_ENTITY_ONLY, BOTH).flatMap(Set::stream).toList());
    }

    @Test
    void codesRoundTripAndUnknownCodesAreEmpty() {
        assertThat(TaxRegime.fromCode("626")).contains(TaxRegime.REGIMEN_SIMPLIFICADO_CONFIANZA);
        assertThat(TaxRegime.fromJson("601")).isEqualTo(TaxRegime.GENERAL_LEY_PERSONAS_MORALES);
        assertThat(TaxRegime.fromCode("600")).isEmpty();
        assertThat(TaxRegime.fromJson("abc")).isNull();
    }

    @Test
    void applicableTypesAreImmutable() {
        assertThatThrownBy(() -> TaxRegime.REGIMEN_SIMPLIFICADO_CONFIANZA.applicableTypes().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
