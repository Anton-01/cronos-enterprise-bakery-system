package com.ninsky.cronos.kitchen.recipe;

import com.ninsky.cronos.kitchen.costing.BaseQuantity;
import com.ninsky.cronos.kitchen.costing.CostEngine;
import com.ninsky.cronos.kitchen.costing.CostIngredient;
import com.ninsky.cronos.kitchen.costing.PriceSource;
import com.ninsky.cronos.kitchen.unit.UnitInfo;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Maps a stored recipe onto a {@link CostEngine.Request}; incompatible or unknown lines count as unpriced (K3). */
final class RecipeCostCalculator {

    private RecipeCostCalculator() {
    }

    static CostEngine.Request request(RecipeAggregate recipe, Map<UUID, CostIngredient> ingredients, Map<Long, UnitInfo> units,
                                      CostEngine.Rules rules) {
        RecipeAggregate.Head head = recipe.head();
        return new CostEngine.Request(
                recipe.lines().stream().map(l -> line(l, ingredients, units)).flatMap(Optional::stream).toList(),
                recipe.fixed().stream().map(RecipeCostCalculator::fixed).toList(),
                head.yieldQuantity(), null, head.wastePercent(), head.targetMarginPercent(), null, rules);
    }

    static Optional<CostEngine.Line> line(RecipeAggregate.Line line, Map<UUID, CostIngredient> ingredients, Map<Long, UnitInfo> units) {
        CostIngredient ingredient = ingredients.get(line.ingredientId());
        UnitInfo unit = units.get(line.unitId());
        if (ingredient == null) {
            return Optional.empty();
        }
        if (unit == null) {
            UnitInfo missing = new UnitInfo(-1L, "?", "?", ingredient.baseDimension().unitDimension(), BigDecimal.ONE, false);
            return Optional.of(CostEngine.Line.of(line.id().toString(), unpriced(ingredient), line.quantity(), missing, line.optional()));
        }
        return Optional.of(CostEngine.Line.of(line.id().toString(), usable(ingredient, unit), line.quantity(), unit, line.optional()));
    }

    static CostEngine.Fixed fixed(RecipeAggregate.Fixed fixed) {
        return new CostEngine.Fixed(fixed.id().toString(), fixed.method(), fixed.defaultAmount(), fixed.masterPercentage(),
                fixed.percentage(), fixed.minutes());
    }

    /** An ingredient whose unit no longer converts (density removed) is costed as unpriced instead of failing. */
    static CostIngredient usable(CostIngredient ingredient, UnitInfo unit) {
        return BaseQuantity.compatible(unit.dimension(), ingredient.baseDimension(), ingredient.densityGPerMl())
                ? ingredient
                : unpriced(ingredient);
    }

    static CostIngredient unpriced(CostIngredient ingredient) {
        return new CostIngredient(ingredient.id(), ingredient.baseDimension(), ingredient.densityGPerMl(), null, PriceSource.NONE);
    }
}
