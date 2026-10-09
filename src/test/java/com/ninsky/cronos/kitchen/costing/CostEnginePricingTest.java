package com.ninsky.cronos.kitchen.costing;

import com.ninsky.cronos.kitchen.costing.CostEngine.Fixed;
import com.ninsky.cronos.kitchen.costing.CostEngine.Line;
import com.ninsky.cronos.kitchen.costing.CostEngine.Request;
import com.ninsky.cronos.kitchen.costing.CostEngine.Result;
import com.ninsky.cronos.kitchen.costing.CostEngine.Rules;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.RoundingMode;
import java.util.List;

import static com.ninsky.cronos.kitchen.costing.CostFixtures.EGG;
import static com.ninsky.cronos.kitchen.costing.CostFixtures.FLOUR;
import static com.ninsky.cronos.kitchen.costing.CostFixtures.KG;
import static com.ninsky.cronos.kitchen.costing.CostFixtures.ML;
import static com.ninsky.cronos.kitchen.costing.CostFixtures.MILK;
import static com.ninsky.cronos.kitchen.costing.CostFixtures.PZ;
import static com.ninsky.cronos.kitchen.costing.CostFixtures.d;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Baking-studio §5.2 (pricing method) and §4.3 (PER_UNIT units per batch). */
class CostEnginePricingTest {

    private static final Rules MXN = new Rules(2, RoundingMode.HALF_UP);

    private final CostEngine engine = new CostEngine();

    /** 34.00 for 10 units → 3.40 per unit. */
    private static List<Line> base() {
        return List.of(Line.of("flour", FLOUR, d("0.5"), KG, false), Line.of("egg", EGG, d("4"), PZ, false),
                Line.of("milk", MILK, d("250"), ML, false));
    }

    private Result price(PricingMethod method, String margin) {
        return engine.calculate(new Request(base(), List.of(), d("10"), null, null, d(margin), method, null, MXN));
    }

    @ParameterizedTest(name = "{0} {1}% → {2}")
    @CsvSource({
            "MARKUP, 0, 3.40",
            "MARKUP, 65, 5.61",
            "MARKUP, 99.9, 6.80",
            "MARGIN, 0, 3.40",
            "MARGIN, 65, 9.71",
            "MARGIN, 99.9, 3400.00"})
    void suggestedPriceFollowsTheMethod(PricingMethod method, String margin, String expected) {
        Result result = price(method, margin);

        assertThat(result.costPerUnit()).isEqualByComparingTo("3.40");
        assertThat(result.suggestedUnitPrice()).isEqualByComparingTo(expected);
        assertThat(result.pricingMethod()).isEqualTo(method);
    }

    @Test
    void marginOfOneHundredPercentHasNoPrice() {
        assertThatThrownBy(() -> price(PricingMethod.MARGIN, "100")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void legacyConstructorKeepsMarkupSoExistingPricesDoNotMove() {
        Result legacy = engine.calculate(new Request(base(), List.of(), d("10"), null, null, d("65"), null, MXN));

        assertThat(legacy.pricingMethod()).isEqualTo(PricingMethod.MARKUP);
        assertThat(legacy.suggestedUnitPrice()).isEqualByComparingTo(price(PricingMethod.MARKUP, "65").suggestedUnitPrice());
    }

    @Test
    void absentMethodDefaultsToMarkup() {
        Result result = engine.calculate(new Request(base(), List.of(), d("10"), null, null, d("50"), null, null, MXN));

        assertThat(result.pricingMethod()).isEqualTo(PricingMethod.MARKUP);
        assertThat(result.suggestedUnitPrice()).isEqualByComparingTo("5.10");
    }

    /** Base yield 10; 2.4 → 3 batches. */
    @ParameterizedTest(name = "target {0}, quantity {1} → {2}")
    @CsvSource({
            "5, , 7.50",
            "10, , 15.00",
            "24, , 36.00",
            "5, 2, 3.00",
            "10, 2, 3.00",
            "24, 2, 9.00"})
    void perUnitChargesPerYieldUnitOrPerBatch(String targetYield, String quantity, String expected) {
        Fixed box = new Fixed("box", FixedCostMethod.PER_UNIT, d("1.5"), null, null, null, quantity == null ? null : d(quantity));

        Result result = engine.calculate(new Request(List.of(), List.of(box), d("10"), d(targetYield), null, null, null, MXN));

        assertThat(result.fixedCost("box")).isEqualByComparingTo(expected);
    }

    @Test
    void quantityIsIgnoredByOtherMethods() {
        Fixed oven = new Fixed("oven", FixedCostMethod.FIXED_PER_BATCH, d("20"), null, null, null, d("5"));

        Result result = engine.calculate(new Request(List.of(), List.of(oven), d("10"), null, null, null, null, MXN));

        assertThat(result.fixedCost("oven")).isEqualByComparingTo("20.00");
    }
}
