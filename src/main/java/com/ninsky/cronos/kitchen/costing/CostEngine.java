package com.ninsky.cronos.kitchen.costing;

import com.ninsky.cronos.kitchen.unit.UnitInfo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The single cost authority (§5.5, K1/K2/K3). Pure and stateless; reused by recipes, cost preview,
 * quotes and the price ripple.
 * <pre>
 * scale = targetYield / baseYield;  batches = ceil(scale)
 * line:  qty = quantity × ratio × scale → base units → lineCost = round(baseQty × costPerBaseUnit) | null
 * ingredients = Σ lineCost;  waste = round(ingredients × waste%)
 * fixed: HOURLY = amount × min/60 × batches; PER_UNIT = amount × targetYield;
 *        PER_BATCH = amount × batches; PERCENTAGE = pct × (ingredients + waste)
 * total = ingredients + waste + Σ fixed;  perUnit = round(total / targetYield)
 * suggested = round(total / targetYield × (1 + margin%))
 * </pre>
 * Scale 10 HALF_EVEN internally; money rounded per line, then summed (CFDI style).
 */
public final class CostEngine {

    public static final int SCALE = 10;
    private static final RoundingMode INTERNAL = RoundingMode.HALF_EVEN;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal SIXTY = BigDecimal.valueOf(60);

    /** Currency decimals and tenant rounding mode (finance settings). */
    public record Rules(int decimals, RoundingMode mode) {
        public Rules {
            Objects.requireNonNull(mode, "mode");
        }

        BigDecimal round(BigDecimal value) {
            return value.setScale(decimals, mode);
        }

        BigDecimal zero() {
            return BigDecimal.ZERO.setScale(decimals, mode);
        }
    }

    /** A stored or draft line; {@code substitute} replaces the ingredient with {@code ratio}. */
    public record Line(String key, CostIngredient ingredient, BigDecimal quantity, UnitInfo unit, boolean optional,
                       CostIngredient substitute, BigDecimal substituteRatio) {
        public Line {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(ingredient, "ingredient");
            Objects.requireNonNull(quantity, "quantity");
            Objects.requireNonNull(unit, "unit");
        }

        public static Line of(String key, CostIngredient ingredient, BigDecimal quantity, UnitInfo unit, boolean optional) {
            return new Line(key, ingredient, quantity, unit, optional, null, null);
        }

        public CostIngredient effectiveIngredient() {
            return substitute == null ? ingredient : substitute;
        }
    }

    /** A fixed cost; {@code percentage} on the recipe overrides {@code masterPercentage}. */
    public record Fixed(String key, FixedCostMethod method, BigDecimal defaultAmount, BigDecimal masterPercentage,
                        BigDecimal percentage, Integer minutes) {
        public Fixed {
            Objects.requireNonNull(method, "method");
        }
    }

    /**
     * @param excludedKeys null = editor mode (optional lines excluded); otherwise a line is included
     *                     iff its key is not in the set (configurator mode)
     */
    public record Request(List<Line> lines, List<Fixed> fixed, BigDecimal baseYield, BigDecimal targetYield,
                          BigDecimal wastePercent, BigDecimal targetMarginPercent, Set<String> excludedKeys, Rules rules) {
        public Request {
            lines = List.copyOf(lines);
            fixed = List.copyOf(fixed);
            Objects.requireNonNull(baseYield, "baseYield");
            Objects.requireNonNull(rules, "rules");
            if (baseYield.signum() <= 0) {
                throw new IllegalArgumentException("baseYield must be positive");
            }
            targetYield = targetYield == null ? baseYield : targetYield;
            if (targetYield.signum() <= 0) {
                throw new IllegalArgumentException("targetYield must be positive");
            }
            wastePercent = wastePercent == null ? BigDecimal.ZERO : wastePercent;
            targetMarginPercent = targetMarginPercent == null ? BigDecimal.ZERO : targetMarginPercent;
        }

        boolean includes(Line line) {
            return excludedKeys == null ? !line.optional() : !excludedKeys.contains(line.key());
        }
    }

    public record LineResult(String key, boolean included, BigDecimal lineCost, PriceSource priceSource) {
    }

    public record FixedResult(String key, BigDecimal cost) {
    }

    public record Result(List<LineResult> lines, List<FixedResult> fixed, BigDecimal ingredientsCost, BigDecimal wasteCost,
                         BigDecimal fixedCosts, BigDecimal totalCost, BigDecimal costPerUnit, BigDecimal suggestedUnitPrice,
                         int unpricedLines) {

        public CostStatus status() {
            return unpricedLines > 0 ? CostStatus.INCOMPLETE : CostStatus.CURRENT;
        }

        public BigDecimal lineCost(String key) {
            return lines.stream().filter(l -> l.key().equals(key)).findFirst().map(LineResult::lineCost).orElse(null);
        }
    }

    public Result calculate(Request request) {
        Rules rules = request.rules();
        BigDecimal scale = request.targetYield().divide(request.baseYield(), SCALE, INTERNAL);
        BigDecimal batches = scale.setScale(0, RoundingMode.CEILING);

        List<LineResult> lines = request.lines().stream().map(line -> line(line, request, scale)).toList();
        BigDecimal ingredients = sum(lines.stream().map(LineResult::lineCost), rules);
        int unpriced = (int) lines.stream().filter(l -> l.included() && l.lineCost() == null).count();
        BigDecimal waste = rules.round(ingredients.multiply(request.wastePercent()).divide(HUNDRED, SCALE, INTERNAL));

        BigDecimal percentageBase = ingredients.add(waste);
        List<FixedResult> fixed = request.fixed().stream()
                .map(f -> new FixedResult(f.key(), rules.round(fixed(f, request.targetYield(), batches, percentageBase))))
                .toList();
        BigDecimal fixedTotal = sum(fixed.stream().map(FixedResult::cost), rules);

        BigDecimal total = ingredients.add(waste).add(fixedTotal);
        BigDecimal perUnitRaw = total.divide(request.targetYield(), SCALE, INTERNAL);
        BigDecimal markup = BigDecimal.ONE.add(request.targetMarginPercent().divide(HUNDRED, SCALE, INTERNAL));
        return new Result(lines, fixed, ingredients, waste, fixedTotal, total, rules.round(perUnitRaw),
                rules.round(perUnitRaw.multiply(markup)), unpriced);
    }

    private static LineResult line(Line line, Request request, BigDecimal scale) {
        CostIngredient ingredient = line.effectiveIngredient();
        if (!request.includes(line)) {
            return new LineResult(line.key(), false, null, ingredient.priceSource());
        }
        if (!ingredient.priced()) {
            return new LineResult(line.key(), true, null, PriceSource.NONE);
        }
        BigDecimal ratio = line.substitute() == null || line.substituteRatio() == null ? BigDecimal.ONE : line.substituteRatio();
        BigDecimal quantity = line.quantity().multiply(ratio).multiply(scale);
        BigDecimal base = BaseQuantity.of(quantity, line.unit(), ingredient.baseDimension(), ingredient.densityGPerMl());
        return new LineResult(line.key(), true, request.rules().round(base.multiply(ingredient.costPerBaseUnit())), ingredient.priceSource());
    }

    private static BigDecimal fixed(Fixed fixed, BigDecimal targetYield, BigDecimal batches, BigDecimal percentageBase) {
        BigDecimal amount = orZero(fixed.defaultAmount());
        return switch (fixed.method()) {
            case HOURLY_RATE -> amount.multiply(BigDecimal.valueOf(fixed.minutes() == null ? 0 : fixed.minutes()))
                    .divide(SIXTY, SCALE, INTERNAL).multiply(batches);
            case PER_UNIT -> amount.multiply(targetYield);
            case FIXED_PER_BATCH -> amount.multiply(batches);
            case PERCENTAGE -> orZero(fixed.percentage() != null ? fixed.percentage() : fixed.masterPercentage())
                    .divide(HUNDRED, SCALE, INTERNAL).multiply(percentageBase);
        };
    }

    private static BigDecimal sum(Stream<BigDecimal> values, Rules rules) {
        return values.filter(Objects::nonNull).reduce(rules.zero(), BigDecimal::add);
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
