package com.ninsky.cronos.kitchen.costing;

import com.ninsky.cronos.kitchen.costing.CostEngine.Fixed;
import com.ninsky.cronos.kitchen.costing.CostEngine.Line;
import com.ninsky.cronos.kitchen.costing.CostEngine.LineResult;
import com.ninsky.cronos.kitchen.costing.CostEngine.Request;
import com.ninsky.cronos.kitchen.costing.CostEngine.Result;
import com.ninsky.cronos.kitchen.costing.CostEngine.Rules;
import com.ninsky.cronos.kitchen.shared.Dimension;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;

import static com.ninsky.cronos.kitchen.costing.CostFixtures.BUTTER;
import static com.ninsky.cronos.kitchen.costing.CostFixtures.CUP;
import static com.ninsky.cronos.kitchen.costing.CostFixtures.EGG;
import static com.ninsky.cronos.kitchen.costing.CostFixtures.FLOUR;
import static com.ninsky.cronos.kitchen.costing.CostFixtures.G;
import static com.ninsky.cronos.kitchen.costing.CostFixtures.KG;
import static com.ninsky.cronos.kitchen.costing.CostFixtures.ML;
import static com.ninsky.cronos.kitchen.costing.CostFixtures.MILK;
import static com.ninsky.cronos.kitchen.costing.CostFixtures.PZ;
import static com.ninsky.cronos.kitchen.costing.CostFixtures.UNPRICED;
import static com.ninsky.cronos.kitchen.costing.CostFixtures.d;
import static com.ninsky.cronos.kitchen.costing.CostFixtures.ingredient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CostEngineTest {

    private static final Rules MXN = new Rules(2, RoundingMode.HALF_UP);

    private final CostEngine engine = new CostEngine();

    /** 0.5 kg flour (12.50) + 4 eggs (14.00) + 250 ml milk (7.50) = 34.00 for 10 units. */
    private static List<Line> base() {
        return List.of(Line.of("flour", FLOUR, d("0.5"), KG, false),
                Line.of("egg", EGG, d("4"), PZ, false),
                Line.of("milk", MILK, d("250"), ML, false));
    }

    private static Request request(List<Line> lines, List<Fixed> fixed, String targetYield, String waste, String margin,
                                   Set<String> excluded, Rules rules) {
        return new Request(lines, fixed, d("10"), targetYield == null ? null : d(targetYield), waste == null ? null : d(waste),
                margin == null ? null : d(margin), excluded, rules);
    }

    private Result run(List<Line> lines, String targetYield) {
        return engine.calculate(request(lines, List.of(), targetYield, null, null, null, MXN));
    }

    @Nested
    class Lines {

        @Test
        void costsMassCountAndVolumeLinesInTheirBaseUnit() {
            Result result = run(base(), null);

            assertThat(result.lineCost("flour")).isEqualByComparingTo("12.50");
            assertThat(result.lineCost("egg")).isEqualByComparingTo("14.00");
            assertThat(result.lineCost("milk")).isEqualByComparingTo("7.50");
            assertThat(result.ingredientsCost()).isEqualByComparingTo("34.00");
            assertThat(result.status()).isEqualTo(CostStatus.CURRENT);
        }

        @Test
        void bridgesMassToVolumeWithDensity() {
            // 103 g of milk / 1.03 g/ml = 100 ml × 0.03
            Result result = run(List.of(Line.of("milk", MILK, d("103"), G, false)), null);

            assertThat(result.lineCost("milk")).isEqualByComparingTo("3.00");
        }

        @Test
        void bridgesVolumeToMassWithDensity() {
            // 1 cup = 240 ml × 0.911 g/ml = 218.64 g × 0.18 = 39.3552
            Result result = run(List.of(Line.of("butter", BUTTER, d("1"), CUP, false)), null);

            assertThat(result.lineCost("butter")).isEqualByComparingTo("39.36");
        }

        @Test
        void rejectsVolumeOnMassWithoutDensity() {
            assertThatThrownBy(() -> run(List.of(Line.of("flour", FLOUR, d("1"), CUP, false)), null))
                    .isInstanceOf(UnitIncompatibleException.class);
        }

        @Test
        void rejectsCountOnMass() {
            assertThatThrownBy(() -> run(List.of(Line.of("flour", FLOUR, d("2"), PZ, false)), null))
                    .isInstanceOf(UnitIncompatibleException.class);
        }

        @Test
        void unpricedLineLeavesCostNullAndMarksIncomplete() {
            Result result = run(List.of(Line.of("flour", FLOUR, d("1"), KG, false), Line.of("vanilla", UNPRICED, d("5"), G, false)), null);

            assertThat(result.lineCost("vanilla")).isNull();
            assertThat(result.lines()).filteredOn(l -> l.key().equals("vanilla")).extracting(LineResult::priceSource)
                    .containsExactly(PriceSource.NONE);
            assertThat(result.unpricedLines()).isEqualTo(1);
            assertThat(result.status()).isEqualTo(CostStatus.INCOMPLETE);
            assertThat(result.ingredientsCost()).isEqualByComparingTo("25.00");
        }

        @Test
        void substituteAppliesItsRatioAndCost() {
            CostIngredient almondFlour = ingredient(Dimension.MASS, null, "0.04");
            // 500 g × 1.2 = 600 g × 0.04
            Line line = new Line("flour", FLOUR, d("500"), G, false, almondFlour, d("1.2"));

            assertThat(run(List.of(line), null).lineCost("flour")).isEqualByComparingTo("24.00");
        }
    }

    @Nested
    class Selection {

        private final List<Line> withOptional = List.of(Line.of("flour", FLOUR, d("1"), KG, false),
                Line.of("glaze", BUTTER, d("100"), G, true));

        @Test
        void editorModeExcludesOptionalLines() {
            Result result = run(withOptional, null);

            assertThat(result.lineCost("glaze")).isNull();
            assertThat(result.lines()).filteredOn(l -> l.key().equals("glaze")).extracting(LineResult::included).containsExactly(false);
            assertThat(result.unpricedLines()).isZero();
            assertThat(result.ingredientsCost()).isEqualByComparingTo("25.00");
        }

        @Test
        void configuratorModeIncludesEveryLineNotExcluded() {
            Result result = engine.calculate(request(withOptional, List.of(), null, null, null, Set.of(), MXN));

            assertThat(result.lineCost("glaze")).isEqualByComparingTo("18.00");
            assertThat(result.ingredientsCost()).isEqualByComparingTo("43.00");
        }

        @Test
        void configuratorModeDropsExcludedKeys() {
            Result result = engine.calculate(request(withOptional, List.of(), null, null, null, Set.of("flour"), MXN));

            assertThat(result.lineCost("flour")).isNull();
            assertThat(result.ingredientsCost()).isEqualByComparingTo("18.00");
        }
    }

    @Nested
    class Scaling {

        @ParameterizedTest(name = "target {0} → flour {1}, egg {2}, milk {3}")
        @CsvSource({
                "5,  6.25, 7.00, 3.75",
                "10, 12.50, 14.00, 7.50",
                "24, 30.00, 33.60, 18.00"
        })
        void scalesEveryLineByTargetOverBaseYield(String target, String flour, String egg, String milk) {
            Result result = run(base(), target);

            assertThat(result.lineCost("flour")).isEqualByComparingTo(flour);
            assertThat(result.lineCost("egg")).isEqualByComparingTo(egg);
            assertThat(result.lineCost("milk")).isEqualByComparingTo(milk);
        }

        @ParameterizedTest(name = "target {0} → {1} batches")
        @CsvSource({"5, 1", "10, 1", "24, 3", "20, 2"})
        void perBatchCostsUseCeilingOfScale(String target, int batches) {
            Fixed oven = new Fixed("oven", FixedCostMethod.FIXED_PER_BATCH, d("20"), null, null, null);

            Result result = engine.calculate(request(List.of(), List.of(oven), target, null, null, null, MXN));

            assertThat(result.fixedCosts()).isEqualByComparingTo(BigDecimal.valueOf(20L * batches));
        }

        @Test
        void rejectsNonPositiveYields() {
            assertThatThrownBy(() -> new Request(List.of(), List.of(), BigDecimal.ZERO, null, null, null, null, MXN))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new Request(List.of(), List.of(), d("10"), d("-1"), null, null, null, MXN))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class WasteAndFixedCosts {

        @ParameterizedTest(name = "waste {0}% → {1}")
        @CsvSource({"0, 0.00", "10, 3.40", "50, 17.00"})
        void wasteIsAPercentageOfIngredients(String waste, String expected) {
            Result result = engine.calculate(request(base(), List.of(), null, waste, null, null, MXN));

            assertThat(result.wasteCost()).isEqualByComparingTo(expected);
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource({
                // method, amount, master %, recipe %, minutes, target, expected
                "HOURLY_RATE,     120, ,   ,  30, 10, 60.00",
                "HOURLY_RATE,     120, ,   ,  30, 24, 180.00",
                "PER_UNIT,        1.5, ,   ,    , 10, 15.00",
                "FIXED_PER_BATCH, 20,  ,   ,    , 10, 20.00",
                "PERCENTAGE,      ,    5,  ,    , 10, 1.87",
                "PERCENTAGE,      ,    5,  8,   , 10, 2.99"
        })
        void everyMethodChargesTheBatch(FixedCostMethod method, String amount, String master, String recipe, Integer minutes,
                                        String target, String expected) {
            Fixed fixed = new Fixed("f", method, amount == null ? null : d(amount), master == null ? null : d(master),
                    recipe == null ? null : d(recipe), minutes);
            List<Line> lines = target.equals("10") ? base() : List.of();

            // Percentage base = ingredients 34.00 + waste 3.40
            Result result = engine.calculate(request(lines, List.of(fixed), target, "10", null, null, MXN));

            assertThat(result.fixedCosts()).isEqualByComparingTo(expected);
        }

        @Test
        void fullRecipeTotalsPerUnitAndSuggestedPrice() {
            List<Fixed> fixed = List.of(new Fixed("labor", FixedCostMethod.HOURLY_RATE, d("120"), null, null, 30),
                    new Fixed("box", FixedCostMethod.PER_UNIT, d("1.5"), null, null, null),
                    new Fixed("oven", FixedCostMethod.FIXED_PER_BATCH, d("20"), null, null, null),
                    new Fixed("overhead", FixedCostMethod.PERCENTAGE, null, d("5"), null, null));

            Result result = engine.calculate(request(base(), fixed, null, "10", "30", null, MXN));

            assertThat(result.fixedCosts()).isEqualByComparingTo("96.87");
            assertThat(result.totalCost()).isEqualByComparingTo("134.27");
            assertThat(result.costPerUnit()).isEqualByComparingTo("13.43");
            // 13.427 × 1.30 = 17.4551
            assertThat(result.suggestedUnitPrice()).isEqualByComparingTo("17.46");
        }
    }

    @Nested
    class Rounding {

        @Test
        void zeroDecimalCurrencyRoundsEachLine() {
            Result result = engine.calculate(request(base(), List.of(), null, null, null, null, new Rules(0, RoundingMode.HALF_EVEN)));

            // 12.5 → 12, 14 → 14, 7.5 → 8
            assertThat(result.lineCost("flour")).isEqualByComparingTo("12");
            assertThat(result.lineCost("milk")).isEqualByComparingTo("8");
            assertThat(result.ingredientsCost()).isEqualByComparingTo("34");
            assertThat(result.ingredientsCost().scale()).isZero();
        }

        @Test
        void fourDecimalCurrencyKeepsPrecision() {
            Result result = engine.calculate(request(List.of(Line.of("butter", BUTTER, d("1"), CUP, false)), List.of(), null, null, null,
                    null, new Rules(4, RoundingMode.HALF_EVEN)));

            assertThat(result.lineCost("butter")).isEqualByComparingTo("39.3552");
            assertThat(result.costPerUnit().scale()).isEqualTo(4);
        }

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource({"HALF_UP, 0.13", "HALF_EVEN, 0.12", "HALF_DOWN, 0.12", "UP, 0.13", "DOWN, 0.12", "CEILING, 0.13", "FLOOR, 0.12"})
        void honoursTheTenantRoundingMode(RoundingMode mode, String expected) {
            // 5 g × 0.025 = 0.125
            Result result = engine.calculate(request(List.of(Line.of("flour", FLOUR, d("5"), G, false)), List.of(), null, null, null,
                    null, new Rules(2, mode)));

            assertThat(result.lineCost("flour")).isEqualByComparingTo(expected);
        }
    }

    @Test
    void totalIsAlwaysTheSumOfRoundedParts() {
        Random random = new Random(42);
        List<CostIngredient> pool = List.of(FLOUR, EGG, MILK, BUTTER, UNPRICED);

        IntStream.range(0, 500).forEach(run -> {
            List<Line> lines = IntStream.range(0, 1 + random.nextInt(8)).mapToObj(i -> {
                CostIngredient ingredient = pool.get(random.nextInt(pool.size()));
                var unit = switch (ingredient.baseDimension()) {
                    case MASS -> G;
                    case VOLUME -> ML;
                    case COUNT -> PZ;
                };
                return Line.of("l" + i, ingredient, BigDecimal.valueOf(1 + random.nextInt(99_999), 3), unit, random.nextInt(5) == 0);
            }).toList();
            List<Fixed> fixed = List.of(new Fixed("labor", FixedCostMethod.HOURLY_RATE, BigDecimal.valueOf(random.nextInt(50_000), 2), null,
                    null, random.nextInt(240)), new Fixed("overhead", FixedCostMethod.PERCENTAGE, null, BigDecimal.valueOf(random.nextInt(3_000), 2),
                    null, null));
            Rules rules = new Rules(random.nextInt(5), RoundingMode.values()[random.nextInt(7)]);
            Request request = new Request(lines, fixed, BigDecimal.valueOf(1 + random.nextInt(50)),
                    BigDecimal.valueOf(1 + random.nextInt(5_000), 1), BigDecimal.valueOf(random.nextInt(5_000), 2), d("35"), null, rules);

            Result result = engine.calculate(request);

            BigDecimal lineSum = result.lines().stream().map(LineResult::lineCost).filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal fixedSum = result.fixed().stream().map(CostEngine.FixedResult::cost).reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(result.ingredientsCost()).isEqualByComparingTo(lineSum);
            assertThat(result.fixedCosts()).isEqualByComparingTo(fixedSum);
            assertThat(result.totalCost()).isEqualByComparingTo(result.ingredientsCost().add(result.wasteCost()).add(result.fixedCosts()));
            assertThat(result.costPerUnit().scale()).isEqualTo(rules.decimals());
        });
    }
}
