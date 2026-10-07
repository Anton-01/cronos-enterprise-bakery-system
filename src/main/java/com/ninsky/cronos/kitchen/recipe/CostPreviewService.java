package com.ninsky.cronos.kitchen.recipe;

import com.ninsky.cronos.iam.shared.ActorProvider;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.infrastructure.exception.Violations;
import com.ninsky.cronos.kitchen.allergen.AllergenCatalog;
import com.ninsky.cronos.kitchen.costing.CostContext;
import com.ninsky.cronos.kitchen.costing.CostEngine;
import com.ninsky.cronos.kitchen.ingredient.IngredientQueryCustomRepository;
import com.ninsky.cronos.kitchen.shared.CostPreviewRateLimiter;
import com.ninsky.cronos.kitchen.shared.KitchenMessages;
import com.ninsky.cronos.kitchen.shared.Numbers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** §5.6: never persists, never audits; rate limited per user (K1: the UI only shows server costs). */
@Service
@RequiredArgsConstructor
public class CostPreviewService {

    private final RecipeCustomRepository store;
    private final RecipeValidator validator;
    private final RecipeConfigurator configurator;
    private final RecipeCosting costing;
    private final IngredientQueryCustomRepository ingredients;
    private final AllergenCatalog allergens;
    private final CostContext costContext;
    private final CostPreviewRateLimiter rateLimiter;
    private final ActorProvider actors;
    private final Clock clock;

    @Transactional(readOnly = true)
    public CostPreview preview(CostPreviewRequest request) {
        UUID tenant = actors.require().id();
        rateLimiter.acquire(tenant);
        String language = KitchenMessages.language();
        AllergenCatalog.View view = allergens.view(tenant);
        Optional<RecipeAggregate> stored = Optional.ofNullable(request.recipeId())
                .map(id -> store.findVisible(id, tenant).orElseThrow(() -> ApiException.notFound("kitchen.recipe.notFound")));
        return request.lines() != null
                ? editor(request, tenant, language, view, stored.orElse(null))
                : configurator(request, tenant, language, view,
                stored.orElseThrow(() -> ApiException.invalid("recipeId", "api.validation.required")));
    }

    /** Unsaved draft: line keys are the request's displayOrder; optional lines are excluded. */
    private CostPreview editor(CostPreviewRequest request, UUID tenant, String language, AllergenCatalog.View view, RecipeAggregate current) {
        Violations violations = new Violations();
        RecipeValidator.Context context = new RecipeValidator.Context(tenant, language, view, current);
        List<RecipeAggregate.Line> lines = validator.lines(request.lines(), "lines", context, violations);
        List<RecipeAggregate.Fixed> fixed = validator.fixed(Optional.ofNullable(request.fixedCosts()).orElse(List.of()), "fixedCosts", tenant, violations);
        violations.invalidIf(!Numbers.within(request.yieldQuantity(), "0.01", "100000", 2), "yieldQuantity", "api.validation.range", "0.01", "100,000");
        violations.throwIfAny();

        List<RecipeAggregate.Line> keyed = java.util.stream.IntStream.range(0, lines.size()).mapToObj(i -> {
            RecipeAggregate.Line l = lines.get(i);
            return new RecipeAggregate.Line(keyId(i), l.ingredientId(), l.section(), l.quantity(), l.unitId(), l.optional(), l.quoteSelectable(),
                    l.notes(), l.displayOrder(), null, l.extraAllergens());
        }).toList();
        RecipeAggregate draft = new RecipeAggregate(new RecipeAggregate.Head(null, null, tenant, null, null, Difficulty.EASY, null, null, null,
                null, null, null, null, null, request.yieldQuantity(), null, RecipeStatus.DRAFT,
                Optional.ofNullable(request.targetMarginPercent()).orElse(RecipeValidator.DEFAULT_MARGIN),
                Optional.ofNullable(request.wastePercent()).orElse(RecipeValidator.DEFAULT_WASTE), null, null, null, null, null, 0), keyed, fixed);
        CostEngine.Result result = costing.evaluate(draft);
        Map<UUID, Integer> orderById = keyed.stream().collect(Collectors.toMap(RecipeAggregate.Line::id, RecipeAggregate.Line::displayOrder,
                (a, b) -> a));
        Map<UUID, List<Long>> declared = ingredients.allergenIds(keyed.stream().map(RecipeAggregate.Line::ingredientId).distinct().toList());
        return response(result, keyed, l -> String.valueOf(orderById.get(l.id())), RecipeAggregate.Line::ingredientId,
                RecipeAllergens.contains(keyed, declared), view, language);
    }

    private CostPreview configurator(CostPreviewRequest request, UUID tenant, String language, AllergenCatalog.View view, RecipeAggregate recipe) {
        RecipeConfigurator.Configured configured = configurator.price(tenant, recipe, request.configuration(), "configuration",
                request.wastePercent(), request.targetMarginPercent(), new Violations());
        return response(configured.result(), recipe.lines(), l -> l.id().toString(), l -> configured.ingredientByLine().get(l.id()),
                configured.allergenIds(), view, language);
    }

    private CostPreview response(CostEngine.Result result, List<RecipeAggregate.Line> lines, Function<RecipeAggregate.Line, String> keyOf,
                                 Function<RecipeAggregate.Line, UUID> ingredientOf, java.util.Set<Long> allergenIds,
                                 AllergenCatalog.View view, String language) {
        Map<String, CostEngine.LineResult> byKey = result.lines().stream().collect(Collectors.toMap(CostEngine.LineResult::key, Function.identity()));
        List<CostPreview.Line> rows = lines.stream().map(l -> {
            CostEngine.LineResult line = byKey.get(l.id().toString());
            return new CostPreview.Line(keyOf.apply(l), ingredientOf.apply(l), line == null ? null : line.lineCost(),
                    line == null ? null : line.priceSource());
        }).toList();
        RecipeDetail.Cost cost = new RecipeDetail.Cost(result.ingredientsCost(), result.wasteCost(), result.fixedCosts(), result.totalCost(),
                result.costPerUnit(), result.suggestedUnitPrice(), costContext.current().currency(), result.status(), result.unpricedLines(),
                clock.instant());
        return new CostPreview(rows, cost, view.refs(allergenIds, language));
    }

    /** Deterministic per-request id for a draft line (engine keys must be unique). */
    private static UUID keyId(int index) {
        return new UUID(0L, index);
    }
}
