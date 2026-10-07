package com.ninsky.cronos.kitchen.recipe;

import com.ninsky.cronos.finance.shared.Changes;
import com.ninsky.cronos.kitchen.costing.CostContext;
import com.ninsky.cronos.kitchen.costing.CostEngine;
import com.ninsky.cronos.kitchen.costing.CostIngredient;
import com.ninsky.cronos.kitchen.costing.EffectivePrices;
import com.ninsky.cronos.kitchen.unit.UnitCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Recalculates stored recipes with today's prices (K4, §4.5): batch-loaded, one engine run each,
 * cost columns + version + revision written per recipe. Caller owns the transaction.
 */
@Component
@RequiredArgsConstructor
public class RecipeCosting {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final RecipeStore store;
    private final RecipeRevisions revisions;
    private final EffectivePrices prices;
    private final UnitCatalog units;
    private final CostContext costContext;
    private final CostEngine engine;

    /** A recipe after recalculation, with what is needed for the below-margin report. */
    public record Outcome(UUID recipeId, UUID ownerId, String name, BigDecimal previousSuggestedPrice, BigDecimal targetMarginPercent,
                          CostEngine.Result result, long version) {

        /** (reference − cost) / cost × 100, 1 decimal; null when the cost is unknown or zero. */
        public BigDecimal marginAt(BigDecimal referencePrice) {
            BigDecimal cost = result.costPerUnit();
            if (referencePrice == null || cost == null || cost.signum() == 0) {
                return null;
            }
            return referencePrice.subtract(cost).multiply(HUNDRED).divide(cost, 1, RoundingMode.HALF_EVEN);
        }
    }

    /** The engine result of a stored recipe without persisting anything. */
    public CostEngine.Result evaluate(RecipeAggregate recipe) {
        Map<UUID, CostIngredient> ingredients = prices.costIngredients(recipe.head().ownerId(), ingredientIds(List.of(recipe)));
        return engine.calculate(RecipeCostCalculator.request(recipe, ingredients, units.snapshot().byId(), costContext.current().rules()));
    }

    public List<Outcome> recalculate(Collection<UUID> recipeIds, UUID actor, Instant now, RecipeRevisions.Reason reason) {
        return recalculateLoaded(store.lockAll(recipeIds), actor, now, reason);
    }

    /** Recalculates already-loaded (and locked) aggregates; prices are resolved per owner. */
    public List<Outcome> recalculateLoaded(List<RecipeAggregate> recipes, UUID actor, Instant now, RecipeRevisions.Reason reason) {
        if (recipes.isEmpty()) {
            return List.of();
        }
        CostEngine.Rules rules = costContext.current().rules();
        var unitMap = units.snapshot().byId();
        Map<Optional<UUID>, List<RecipeAggregate>> byOwner = recipes.stream()
                .collect(Collectors.groupingBy(r -> Optional.ofNullable(r.head().ownerId())));
        return byOwner.entrySet().stream().flatMap(group -> {
            Map<UUID, CostIngredient> ingredients = prices.costIngredients(group.getKey().orElse(null), ingredientIds(group.getValue()));
            return group.getValue().stream().map(recipe -> {
                CostEngine.Result result = engine.calculate(RecipeCostCalculator.request(recipe, ingredients, unitMap, rules));
                return persist(recipe, result, actor, now, reason);
            });
        }).toList();
    }

    /** Stores a result as the next version with its revision. */
    public Outcome persist(RecipeAggregate recipe, CostEngine.Result result, UUID actor, Instant now, RecipeRevisions.Reason reason) {
        RecipeAggregate.Head head = recipe.head();
        long version = head.version() + 1;
        store.storeCost(head.id(), result, now, version);
        Map<String, Object> changes = Changes.start()
                .track("costPerUnit", head.cost().costPerUnit(), result.costPerUnit())
                .track("totalCost", head.cost().totalCost(), result.totalCost())
                .track("costStatus", head.cost().status(), result.status())
                .build();
        revisions.write(head.id(), version, actor, now, reason, changes, result.costPerUnit());
        return new Outcome(head.id(), head.ownerId(), head.name(), head.cost().suggestedUnitPrice(), head.targetMarginPercent(), result, version);
    }

    private static List<UUID> ingredientIds(List<RecipeAggregate> recipes) {
        return recipes.stream().flatMap(r -> r.lines().stream()).map(RecipeAggregate.Line::ingredientId)
                .filter(Objects::nonNull).distinct().toList();
    }
}
