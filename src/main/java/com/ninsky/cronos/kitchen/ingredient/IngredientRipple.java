package com.ninsky.cronos.kitchen.ingredient;

import com.ninsky.cronos.kitchen.job.KitchenJobs;
import com.ninsky.cronos.kitchen.job.RecalculationJob;
import com.ninsky.cronos.kitchen.recipe.QuoteFlagCustomRepository;
import com.ninsky.cronos.kitchen.recipe.RecipeCosting;
import com.ninsky.cronos.kitchen.recipe.RecipeRevisions;
import com.ninsky.cronos.kitchen.recipe.RecipeCustomRepository;
import com.ninsky.cronos.kitchen.shared.KitchenCaches;
import com.ninsky.cronos.kitchen.shared.KitchenProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Consequences of an ingredient change on recipes and quotes (K4, §4.5, §5.4). Runs in the caller's transaction. */
@Component
@RequiredArgsConstructor
public class IngredientRipple {

    static final String PRICE_CHANGED = "kitchen.revision.priceChanged";
    static final String INGREDIENT_CHANGED = "kitchen.revision.ingredientChanged";

    private final RecipeCustomRepository recipes;
    private final RecipeCosting costing;
    private final RecipeRevisions revisions;
    private final QuoteFlagCustomRepository quoteFlags;
    private final KitchenJobs jobs;
    private final KitchenProperties properties;
    private final KitchenCaches caches;

    /** What the caller's own data went through. */
    public record Result(int recipesAffected, int quotesFlagged, List<RecipeCosting.Outcome> outcomes, PriceImpact.Status status) {
    }

    /** Recalculates the tenant's recipes using the ingredient inline (≤ limit) or queues them (K4). */
    public Result recalculateForTenant(UUID tenant, UUID ingredientId, String reasonKey, String ingredientName, UUID actor, Instant now) {
        List<UUID> ids = recipes.idsUsingIngredient(ingredientId, tenant);
        caches.evict(KitchenCaches.STATS, KitchenCaches.statsKey("recipes", tenant));
        if (ids.isEmpty()) {
            return new Result(0, 0, List.of(), PriceImpact.Status.DONE);
        }
        if (ids.size() > properties.synchronousRippleLimit()) {
            recipes.markStale(ids);
            jobs.enqueue(new RecalculationJob(ingredientId, ids, reasonKey, List.of(ingredientName), actor));
            return new Result(ids.size(), 0, List.of(), PriceImpact.Status.PENDING);
        }
        List<RecipeCosting.Outcome> outcomes = costing.recalculate(ids, actor, now, RecipeRevisions.Reason.of(reasonKey, ingredientName));
        return new Result(ids.size(), quoteFlags.flagOpenQuotes(tenant, ids), outcomes, PriceImpact.Status.DONE);
    }

    /** A platform edit (SYSTEM row): every owner's recipes go STALE and are recalculated in the background. */
    public void everywhere(UUID ingredientId, String ingredientName, UUID actor) {
        List<UUID> ids = recipes.idsUsingIngredientAnywhere(ingredientId);
        if (!ids.isEmpty()) {
            recipes.markStale(ids);
            jobs.enqueue(new RecalculationJob(ingredientId, ids, INGREDIENT_CHANGED, List.of(ingredientName), actor));
            caches.clear(KitchenCaches.STATS);
        }
    }

    /** A new reference price: recipes of owners without an own price, in the background (§4.5 step 5). */
    public void referencePriceChanged(UUID ingredientId, String ingredientName, UUID actor) {
        recipes.markStale(recipes.idsUsingIngredientAnywhere(ingredientId));
        jobs.enqueue(new RecalculationJob(ingredientId, null, PRICE_CHANGED, List.of(ingredientName), actor));
        caches.clear(KitchenCaches.STATS);
    }

    /** Deactivation keeps recipes intact but flags them STALE. */
    public void deactivated(UUID ingredientId) {
        if (recipes.markStale(recipes.idsUsingIngredientAnywhere(ingredientId)) > 0) {
            caches.clear(KitchenCaches.STATS);
        }
    }

    /** Declared allergens changed: derived recipe allergens change too (revision + open quotes flagged). */
    public void allergensChanged(UUID ingredientId, String ingredientName, UUID actor, Instant now) {
        List<UUID> touched = revisions.allergensChanged(ingredientId, ingredientName, actor, now);
        quoteFlags.flagOpenQuotes(null, touched);
    }

    /**
     * Recipes now under their target margin at their reference price: the latest ACCEPTED quote unit price,
     * else the suggested price before this change.
     */
    public List<PriceImpact.BelowMargin> belowMargin(UUID tenant, List<RecipeCosting.Outcome> outcomes) {
        Map<UUID, BigDecimal> accepted = quoteFlags.acceptedPrices(tenant, outcomes.stream().map(RecipeCosting.Outcome::recipeId).toList());
        return outcomes.stream()
                .map(o -> {
                    BigDecimal reference = accepted.getOrDefault(o.recipeId(), o.previousSuggestedPrice());
                    BigDecimal margin = o.marginAt(reference);
                    return margin != null && o.targetMarginPercent() != null && margin.compareTo(o.targetMarginPercent()) < 0
                            ? new PriceImpact.BelowMargin(o.recipeId(), o.name(), margin) : null;
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(PriceImpact.BelowMargin::marginPercent))
                .toList();
    }
}
