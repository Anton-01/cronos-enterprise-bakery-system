package com.ninsky.cronos.domain.service.core;

import com.ninsky.cronos.domain.entity.enums.UnitDimension;
import com.ninsky.cronos.domain.service.core.MeasurementUnitRules.Violation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MeasurementUnitRulesTest {

    @Test
    void factorMustBePresentPositiveAndFitTheColumn() {
        assertThat(MeasurementUnitRules.checkFactor(null, false)).contains(Violation.FACTOR_REQUIRED);
        assertThat(MeasurementUnitRules.checkFactor(BigDecimal.ZERO, false)).contains(Violation.FACTOR_NOT_POSITIVE);
        assertThat(MeasurementUnitRules.checkFactor(new BigDecimal("-1"), false)).contains(Violation.FACTOR_NOT_POSITIVE);
        assertThat(MeasurementUnitRules.checkFactor(new BigDecimal("0.00000000001"), false)).contains(Violation.FACTOR_PRECISION);
        assertThat(MeasurementUnitRules.checkFactor(new BigDecimal("12345678901"), false)).contains(Violation.FACTOR_PRECISION);
        assertThat(MeasurementUnitRules.checkFactor(new BigDecimal("29.5735295625"), false)).isEmpty();
        assertThat(MeasurementUnitRules.checkFactor(new BigDecimal("1000.0000000000000"), false)).as("trailing zeros are not precision").isEmpty();
    }

    @Test
    void baseUnitFactorMustBeExactlyOne() {
        assertThat(MeasurementUnitRules.checkFactor(new BigDecimal("1.000"), true)).isEmpty();
        assertThat(MeasurementUnitRules.checkFactor(new BigDecimal("1000"), true)).contains(Violation.BASE_FACTOR_MUST_BE_ONE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"g", "kg", "T", "t", "fl_oz", "cup_m", "m2", "1"})
    void acceptsIdentifierCodes(String code) {
        assertThat(MeasurementUnitRules.checkCode(code)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " g", "fl oz", "_g", "=cmd", "abcdefghijklmnopqrstu"})
    void rejectsMalformedCodes(String code) {
        assertThat(MeasurementUnitRules.checkCode(code)).contains(Violation.CODE_FORMAT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"=HYPERLINK(\"x\")", "+1", "-1", "@SUM(A1)"})
    void rejectsSpreadsheetFormulaTriggersInDisplayText(String text) {
        assertThat(MeasurementUnitRules.checkDisplayText(text)).contains(Violation.TEXT_FORMULA_INJECTION);
    }

    @Test
    void parsesDimensionsByNameOnly() {
        assertThat(UnitDimension.parse(" mass ")).contains(UnitDimension.MASS);
        assertThat(UnitDimension.parse("MASA")).isEmpty();
        assertThat(UnitDimension.MASS.isDensityBridgeableWith(UnitDimension.VOLUME)).isTrue();
        assertThat(UnitDimension.COUNT.isDensityBridgeableWith(UnitDimension.MASS)).isFalse();
    }
}
