package com.ninsky.cronos.kitchen.costing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.ninsky.cronos.kitchen.costing.CostFixtures.d;
import static org.assertj.core.api.Assertions.assertThat;

class PricingMethodTest {

    /** The quote BELOW_TARGET_MARGIN comparison (§5.3): markup over cost, margin over price. */
    @ParameterizedTest(name = "{0}: price {1}, cost {2} → {3}%")
    @CsvSource({
            "MARKUP, 165, 100, 65.0",
            "MARGIN, 165, 100, 39.4",
            "MARGIN, 285.71, 100, 65.0",
            "MARKUP, 90, 100, -10.0"})
    void achievedPercentageUsesTheMethodsBase(PricingMethod method, String price, String cost, String expected) {
        assertThat(method.achieved(d(price), d(cost))).isEqualByComparingTo(expected);
    }

    @Test
    void achievedIsUndefinedWithoutABase() {
        assertThat(PricingMethod.MARKUP.achieved(d("10"), d("0"))).isNull();
        assertThat(PricingMethod.MARGIN.achieved(d("0"), d("5"))).isNull();
        assertThat(PricingMethod.MARGIN.achieved(null, d("5"))).isNull();
    }

    @Test
    void rangesDifferPerMethod() {
        assertThat(PricingMethod.MARKUP.accepts(d("1000"))).isTrue();
        assertThat(PricingMethod.MARKUP.accepts(d("1000.01"))).isFalse();
        assertThat(PricingMethod.MARGIN.accepts(d("99.99"))).isTrue();
        assertThat(PricingMethod.MARGIN.accepts(d("100"))).isFalse();
        assertThat(PricingMethod.MARGIN.accepts(d("-1"))).isFalse();
    }
}
