package com.ninsky.cronos.finance.pricing;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PricingCalculatorTest {

    private final PricingCalculator calculator = new PricingCalculator();

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    private static PricingRules tasa(String rate, int decimals, boolean includeTax, FinanceRoundingMode mode) {
        return new PricingRules(decimals, TaxFactorType.TASA, d(rate), includeTax, mode);
    }

    private static PriceLine line(String qty, String price) {
        return new PriceLine(d(qty), d(price));
    }

    @Nested
    class TaxExcluded {

        @Test
        void sixteenPercentRoundsNetAndTaxAtCurrencyScale() {
            PricingResult result = calculator.calculate(tasa("16", 2, false, FinanceRoundingMode.HALF_UP),
                    List.of(line("2", "10.555")), null, null);

            assertThat(result.subtotal()).isEqualByComparingTo("21.11");
            assertThat(result.tax()).isEqualTo(d("3.38"));
            assertThat(result.total()).isEqualTo(d("24.49"));
        }

        @Test
        void roundsPerLineThenSumsLikeCfdi() {
            PricingResult result = calculator.calculate(tasa("16", 2, false, FinanceRoundingMode.HALF_UP),
                    List.of(line("1", "0.05"), line("1", "0.05"), line("1", "0.05")), null, null);

            // Global rounding would give 0.15 × 0.16 = 0.024 → 0.02; per line each 0.008 → 0.01.
            assertThat(result.lines()).extracting(LineAmounts::tax).containsOnly(d("0.01"));
            assertThat(result.tax()).isEqualTo(d("0.03"));
            assertThat(result.subtotal()).isEqualTo(d("0.15"));
        }

        @Test
        void zeroPercentHasNoTax() {
            PricingResult result = calculator.calculate(tasa("0", 2, false, FinanceRoundingMode.HALF_UP),
                    List.of(line("3", "19.99")), null, null);

            assertThat(result.tax()).isEqualTo(d("0.00"));
            assertThat(result.total()).isEqualTo(d("59.97"));
        }

        @Test
        void exemptHasNoRateAndNoTax() {
            PricingRules exempt = new PricingRules(2, TaxFactorType.EXENTO, d("16"), false, FinanceRoundingMode.HALF_UP);

            PricingResult result = calculator.calculate(exempt, List.of(line("1", "100")), null, null);

            assertThat(exempt.ratePercent()).isNull();
            assertThat(exempt.effectiveRatePercent()).isEqualTo(BigDecimal.ZERO);
            assertThat(result.tax()).isEqualTo(d("0.00"));
            assertThat(result.total()).isEqualTo(d("100.00"));
        }

        @Test
        void zeroDecimalCurrencyRoundsToUnits() {
            PricingResult result = calculator.calculate(tasa("16", 0, false, FinanceRoundingMode.HALF_UP),
                    List.of(line("3", "333.5")), null, null);

            assertThat(result.subtotal()).isEqualTo(d("1001"));
            assertThat(result.tax()).isEqualTo(d("160"));
            assertThat(result.total()).isEqualTo(d("1161"));
        }

        @Test
        void fourDecimalCurrencyKeepsFourDecimals() {
            PricingResult result = calculator.calculate(tasa("16", 4, false, FinanceRoundingMode.HALF_UP),
                    List.of(line("1", "1.23456")), null, null);

            assertThat(result.subtotal()).isEqualTo(d("1.2346"));
            assertThat(result.tax()).isEqualTo(d("0.1975"));
        }

        @Test
        void largeQuantitiesStayExact() {
            PricingResult result = calculator.calculate(tasa("16", 2, false, FinanceRoundingMode.HALF_UP),
                    List.of(line("1000000", "99999.99")), null, null);

            assertThat(result.subtotal()).isEqualTo(d("99999990000.00"));
            assertThat(result.tax()).isEqualTo(d("15999998400.00"));
            assertThat(result.total()).isEqualTo(d("115999988400.00"));
        }

        @Test
        void fractionalRatesAreApplied() {
            PricingResult result = calculator.calculate(tasa("8.5", 2, false, FinanceRoundingMode.HALF_UP),
                    List.of(line("1", "100")), null, null);

            assertThat(result.tax()).isEqualTo(d("8.50"));
        }
    }

    @Nested
    class TaxIncluded {

        @Test
        void splitsGrossExactly() {
            PricingResult result = calculator.calculate(tasa("16", 2, true, FinanceRoundingMode.HALF_UP),
                    List.of(line("1", "116"), line("1", "100")), null, null);

            assertThat(result.lines().get(0)).isEqualTo(new LineAmounts(d("100.00"), d("16.00"), d("116.00")));
            // 100 / 1.16 = 86.2068… → 86.21; tax is the remainder.
            assertThat(result.lines().get(1)).isEqualTo(new LineAmounts(d("86.21"), d("13.79"), d("100.00")));
            assertThat(result.total()).isEqualTo(d("216.00"));
        }

        @Test
        void exemptKeepsTheWholeGrossAsNet() {
            PricingRules exempt = new PricingRules(2, TaxFactorType.EXENTO, null, true, FinanceRoundingMode.HALF_UP);

            LineAmounts amounts = calculator.line(exempt, line("2", "50"));

            assertThat(amounts).isEqualTo(new LineAmounts(d("100.00"), d("0.00"), d("100.00")));
        }

        @Test
        void netPlusTaxAlwaysEqualsGross() {
            Random random = new Random(42);
            List<PricingRules> rules = Stream.of(FinanceRoundingMode.values())
                    .flatMap(mode -> Stream.of(tasa("16", 2, true, mode), tasa("8", 0, true, mode), tasa("15.5", 4, true, mode)))
                    .toList();

            IntStream.range(0, 500).forEach(i -> {
                PriceLine priceLine = new PriceLine(BigDecimal.valueOf(random.nextInt(1, 50)),
                        BigDecimal.valueOf(random.nextLong(1, 10_000_000), 3));
                rules.forEach(rule -> {
                    LineAmounts amounts = calculator.line(rule, priceLine);
                    assertThat(amounts.net().add(amounts.tax())).isEqualTo(amounts.gross());
                    assertThat(amounts.net().scale()).isEqualTo(rule.decimalPlaces());
                });
            });
        }

        @Test
        void zeroDecimalGrossSplit() {
            LineAmounts amounts = calculator.line(tasa("16", 0, true, FinanceRoundingMode.HALF_UP), line("1", "1000"));

            assertThat(amounts).isEqualTo(new LineAmounts(d("862"), d("138"), d("1000")));
        }
    }

    @ParameterizedTest(name = "{0}: 2.345 → {1}, 2.355 → {2}")
    @CsvSource({"HALF_UP, 2.35, 2.36", "HALF_EVEN, 2.34, 2.36", "UP, 2.35, 2.36", "DOWN, 2.34, 2.35"})
    void everyRoundingModeIsHonoured(FinanceRoundingMode mode, String first, String second) {
        PricingRules rules = tasa("0", 2, false, mode);

        assertThat(calculator.line(rules, line("1", "2.345")).net()).isEqualTo(d(first));
        assertThat(calculator.line(rules, line("1", "2.355")).net()).isEqualTo(d(second));
    }

    @ParameterizedTest
    @EnumSource(FinanceRoundingMode.class)
    void roundingModeAlsoAppliesToTax(FinanceRoundingMode mode) {
        // 10.03 × 16 % = 1.6048
        BigDecimal tax = calculator.line(tasa("16", 2, false, mode), line("1", "10.03")).tax();

        assertThat(tax).isEqualTo(switch (mode) {
            case HALF_UP, HALF_EVEN, DOWN -> d("1.60");
            case UP -> d("1.61");
        });
    }

    @Test
    void feesAreRoundedAndAddedUntaxed() {
        PricingResult result = calculator.calculate(tasa("16", 2, false, FinanceRoundingMode.HALF_UP),
                List.of(line("1", "100")), d("50.005"), d("10"));

        assertThat(result.deliveryFee()).isEqualTo(d("50.01"));
        assertThat(result.extraFee()).isEqualTo(d("10.00"));
        assertThat(result.total()).isEqualTo(d("176.01"));
    }

    @Test
    void emptyDocumentIsZero() {
        PricingResult result = calculator.calculate(tasa("16", 2, false, FinanceRoundingMode.HALF_UP), List.of(), null, null);

        assertThat(result.total()).isEqualTo(d("0.00"));
        assertThat(result.lines()).isEmpty();
    }

    static Stream<Arguments> invalidInputs() {
        return Stream.of(
                Arguments.of("negative quantity", (Runnable) () -> line("-1", "10")),
                Arguments.of("negative price", (Runnable) () -> line("1", "-0.01")),
                Arguments.of("null price", (Runnable) () -> new PriceLine(BigDecimal.ONE, null)),
                Arguments.of("rate above 100", (Runnable) () -> tasa("100.0001", 2, false, FinanceRoundingMode.HALF_UP)),
                Arguments.of("negative rate", (Runnable) () -> tasa("-1", 2, false, FinanceRoundingMode.HALF_UP)),
                Arguments.of("rate scale 5", (Runnable) () -> tasa("16.00001", 2, false, FinanceRoundingMode.HALF_UP)),
                Arguments.of("TASA without rate", (Runnable) () -> new PricingRules(2, TaxFactorType.TASA, null, false, FinanceRoundingMode.HALF_UP)),
                Arguments.of("5 decimals", (Runnable) () -> tasa("16", 5, false, FinanceRoundingMode.HALF_UP)),
                Arguments.of("negative decimals", (Runnable) () -> tasa("16", -1, false, FinanceRoundingMode.HALF_UP)));
    }

    @ParameterizedTest(name = "{0} is rejected")
    @MethodSource("invalidInputs")
    void invalidInputIsRejected(String name, Runnable construction) {
        assertThatThrownBy(construction::run).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativeFeesAreRejected() {
        PricingRules rules = tasa("16", 2, false, FinanceRoundingMode.HALF_UP);

        assertThatThrownBy(() -> calculator.calculate(rules, List.of(), d("-1"), null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculator.calculate(rules, List.of(), null, d("-0.01"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hundredPercentAndFourDecimalRatesAreValid() {
        assertThat(PricingRules.isValidRate(d("100"))).isTrue();
        assertThat(PricingRules.isValidRate(d("16.1234"))).isTrue();
        assertThat(PricingRules.isValidRate(d("16.12340"))).isTrue();
        assertThat(PricingRules.isValidRate(null)).isFalse();
    }
}
