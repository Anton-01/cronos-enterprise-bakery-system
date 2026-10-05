package com.ninsky.cronos.finance;

import com.ninsky.cronos.finance.currency.CurrencyDraft;
import com.ninsky.cronos.finance.currency.CurrencyRules;
import com.ninsky.cronos.finance.currency.SymbolPosition;
import com.ninsky.cronos.finance.pricing.TaxFactorType;
import com.ninsky.cronos.finance.shared.Changes;
import com.ninsky.cronos.finance.shared.FieldIssue;
import com.ninsky.cronos.finance.shared.SqlSupport;
import com.ninsky.cronos.finance.taxrate.TaxRateDraft;
import com.ninsky.cronos.finance.taxrate.TaxRateRules;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure domain rules of currencies and IVA rates (spec §9.2, §10.2). */
class FinanceRulesTest {

    @Nested
    class Currencies {

        @ParameterizedTest
        @CsvSource({"MXN, 484", "USD, 840", "EUR, 978", "CLP, 152", "JPY, 392"})
        void isoCodesWithTheirNumericCodePass(String code, String numeric) {
            assertThat(CurrencyRules.check(code, numeric)).isEmpty();
        }

        @Test
        void unknownCodeIsRejectedOnCode() {
            assertThat(CurrencyRules.check("ABC", "999")).extracting(FieldIssue::field, FieldIssue::messageKey)
                    .containsExactly(org.assertj.core.groups.Tuple.tuple("code", "finance.currency.code.notIso"));
        }

        @Test
        void numericCodeMustMatchIso() {
            assertThat(CurrencyRules.check("MXN", "840")).extracting(FieldIssue::field).containsExactly("numericCode");
            assertThat(CurrencyRules.isoNumeric("ARS")).contains("032");
        }

        @Test
        void malformedValuesAreLeftToBeanValidation() {
            assertThat(CurrencyRules.check("mx", "84")).isEmpty();
            assertThat(CurrencyRules.check(null, null)).isEmpty();
            assertThat(CurrencyRules.check("MXN", "8a4")).isEmpty();
        }

        @Test
        void codeNumericAndDecimalsAreFrozenWhenInUse() {
            CurrencyDraft current = new CurrencyDraft("MXN", "484", "Peso", "$", 2, SymbolPosition.BEFORE);

            assertThat(new CurrencyDraft("MXN", "484", "Peso mexicano", "MX$", 2, SymbolPosition.AFTER).immutableFieldsChangedFrom(current))
                    .isEmpty();
            assertThat(new CurrencyDraft("USD", "840", "Peso", "$", 0, SymbolPosition.BEFORE).immutableFieldsChangedFrom(current))
                    .containsExactly("code", "numericCode", "decimalPlaces");
        }
    }

    @Nested
    class TaxRates {

        private final LocalDate from = LocalDate.of(2026, 1, 1);

        @Test
        void exemptMustHaveNoRate() {
            assertThat(TaxRateRules.check(TaxFactorType.EXENTO, BigDecimal.ZERO, from, null))
                    .extracting(FieldIssue::field, FieldIssue::messageKey)
                    .containsExactly(org.assertj.core.groups.Tuple.tuple("ratePercent", "finance.taxRate.ratePercent.exento"));
            assertThat(TaxRateRules.check(TaxFactorType.EXENTO, null, from, null)).isEmpty();
        }

        @Test
        void tasaNeedsARateInRangeWithFourDecimalsAtMost() {
            assertThat(TaxRateRules.check(TaxFactorType.TASA, null, from, null)).extracting(FieldIssue::messageKey)
                    .containsExactly("api.validation.required");
            assertThat(TaxRateRules.check(TaxFactorType.TASA, new BigDecimal("100.5"), from, null)).extracting(FieldIssue::messageKey)
                    .containsExactly("finance.taxRate.ratePercent.range");
            assertThat(TaxRateRules.check(TaxFactorType.TASA, new BigDecimal("16.00001"), from, null)).hasSize(1);
            assertThat(TaxRateRules.check(TaxFactorType.TASA, new BigDecimal("-1"), from, null)).hasSize(1);
            assertThat(TaxRateRules.check(TaxFactorType.TASA, new BigDecimal("0"), from, null)).isEmpty();
            assertThat(TaxRateRules.check(TaxFactorType.TASA, new BigDecimal("16.0000"), from, null)).isEmpty();
        }

        @Test
        void validToCannotPrecedeValidFrom() {
            assertThat(TaxRateRules.check(TaxFactorType.TASA, BigDecimal.TEN, from, from.minusDays(1)))
                    .extracting(FieldIssue::field).containsExactly("validTo");
            assertThat(TaxRateRules.check(TaxFactorType.TASA, BigDecimal.TEN, from, from)).isEmpty();
        }

        @Test
        void allIssuesAreReportedTogether() {
            assertThat(TaxRateRules.check(TaxFactorType.EXENTO, BigDecimal.ONE, from, from.minusDays(1)))
                    .extracting(FieldIssue::field).containsExactly("ratePercent", "validTo");
        }

        @Test
        void validityWindowIsInclusive() {
            LocalDate to = LocalDate.of(2026, 12, 31);
            assertThat(TaxRateRules.isValidOn(from, to, from)).isTrue();
            assertThat(TaxRateRules.isValidOn(from, to, to)).isTrue();
            assertThat(TaxRateRules.isValidOn(from, to, to.plusDays(1))).isFalse();
            assertThat(TaxRateRules.isValidOn(from, to, from.minusDays(1))).isFalse();
            assertThat(TaxRateRules.isValidOn(from, null, LocalDate.of(2100, 1, 1))).isTrue();
        }

        @Test
        void onlyNameDescriptionAndValidToMayChangeWhenInUse() {
            TaxRateDraft current = new TaxRateDraft("IVA_16", "IVA 16", null, TaxFactorType.TASA, new BigDecimal("16.0000"), from, null);

            assertThat(new TaxRateDraft("IVA_16", "IVA general", "x", TaxFactorType.TASA, new BigDecimal("16"), from, from.plusYears(1))
                    .immutableFieldsChangedFrom(current)).isEmpty();
            assertThat(new TaxRateDraft("IVA_X", "IVA 16", null, TaxFactorType.EXENTO, null, from.plusDays(1), null)
                    .immutableFieldsChangedFrom(current)).containsExactly("code", "factorType", "ratePercent", "validFrom");
        }
    }

    @Nested
    class AuditChanges {

        @Test
        void onlyChangedFieldsAreKept() {
            Map<String, Object> changes = Changes.start()
                    .track("name", "Peso", "Peso mexicano")
                    .track("rate", new BigDecimal("16.0000"), new BigDecimal("16"))
                    .track("factor", TaxFactorType.TASA, TaxFactorType.EXENTO)
                    .track("validTo", null, LocalDate.of(2026, 12, 31))
                    .build();

            assertThat(changes).containsOnlyKeys("name", "factor", "validTo");
            assertThat(changes.get("factor")).isEqualTo(Map.of("from", "TASA", "to", "EXENTO"));
            java.util.HashMap<String, Object> expected = new java.util.HashMap<>();
            expected.put("from", null);
            expected.put("to", "2026-12-31");
            assertThat(changes.get("validTo")).isEqualTo(expected);
        }
    }

    @Test
    void likePatternEscapesWildcards() {
        assertThat(SqlSupport.likePattern("  50%_off ")).isEqualTo("%50\\%\\_off%");
        assertThat(SqlSupport.likePattern(" ")).isNull();
    }
}
