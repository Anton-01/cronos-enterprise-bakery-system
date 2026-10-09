package com.ninsky.cronos.kitchen.recipe;

import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.infrastructure.exception.Violations;
import com.ninsky.cronos.kitchen.costing.CostEngine;
import com.ninsky.cronos.kitchen.costing.CostStatus;
import com.ninsky.cronos.kitchen.costing.PricingMethod;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** §5.3: margin ranges per method and the ripple's below-margin comparison. */
class RecipeMarginRulesTest {

    @ParameterizedTest(name = "{0} {1}% valid={2}")
    @CsvSource({
            "MARKUP, 0, true",
            "MARKUP, 1000, true",
            "MARKUP, 1000.01, false",
            "MARGIN, 99.99, true",
            "MARGIN, 100, false",
            "MARGIN, 65.123, false",
            "MARGIN, -1, false"})
    void marginRangeDependsOnTheMethod(PricingMethod method, String margin, boolean valid) {
        Violations violations = new Violations();
        RecipeValidator.margin(violations, "targetMarginPercent", new BigDecimal(margin), method);

        boolean passed;
        try {
            violations.throwIfAny();
            passed = true;
        } catch (ApiException e) {
            passed = false;
            assertThat(e.violations().getFirst().field()).isEqualTo("targetMarginPercent");
        }
        assertThat(passed).isEqualTo(valid);
    }

    /** Cost 10, reference price 15: markup 50 %, margin 33.3 %. */
    @ParameterizedTest(name = "{0} target {1}% → below={2}")
    @CsvSource({
            "MARKUP, 50, false",
            "MARKUP, 60, true",
            "MARGIN, 33, false",
            "MARGIN, 40, true"})
    void rippleComparesInTheRecipesOwnTerms(PricingMethod method, String target, boolean below) {
        CostEngine.Result result = new CostEngine.Result(List.of(), List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN,
                BigDecimal.TEN, BigDecimal.TEN, 0, method);
        RecipeCosting.Outcome outcome = new RecipeCosting.Outcome(UUID.randomUUID(), null, "R", new BigDecimal("15"), new BigDecimal(target),
                method, result, 1);

        BigDecimal achieved = outcome.marginAt(new BigDecimal("15"));

        assertThat(achieved.compareTo(new BigDecimal(target)) < 0).isEqualTo(below);
        assertThat(result.status()).isEqualTo(CostStatus.CURRENT);
    }
}
