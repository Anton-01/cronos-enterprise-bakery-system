package com.ninsky.cronos.kitchen.legacy;

import com.ninsky.cronos.application.request.recipe.RecipeFixedCostRequest;
import com.ninsky.cronos.application.request.recipe.RecipeIngredientRequest;
import com.ninsky.cronos.iam.shared.ActorProvider;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.kitchen.recipe.CostPreview;
import com.ninsky.cronos.kitchen.recipe.CostPreviewRequest;
import com.ninsky.cronos.kitchen.recipe.CostPreviewService;
import com.ninsky.cronos.kitchen.recipe.RecipeAggregate;
import com.ninsky.cronos.kitchen.recipe.RecipeConfiguration;
import com.ninsky.cronos.kitchen.recipe.RecipeRequest;
import com.ninsky.cronos.kitchen.recipe.RecipeService;
import com.ninsky.cronos.kitchen.recipe.RecipeCustomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * Deprecated per-line recipe edits (§13): each call rebuilds the whole {@link RecipeRequest}, applies
 * one change and saves through {@link RecipeService#update}, so validation, costing and revisions match
 * the new API exactly. Legacy recipe-ingredient ids are the line ids (V19 kept them).
 */
@Service
@RequiredArgsConstructor
public class LegacyRecipeEditor {

    private final RecipeCustomRepository store;
    private final RecipeService recipes;
    private final CostPreviewService previews;
    private final ActorProvider actors;

    @Transactional
    public void addLine(UUID recipeId, RecipeIngredientRequest line) {
        edit(recipeId, lines -> {
            int order = line.displayOrder() != null ? line.displayOrder() : lines.size();
            return Stream.concat(lines.stream(), Stream.of(new RecipeRequest.LineRequest(null, line.rawMaterialId(), null, line.quantity(),
                    line.unitId(), line.isOptional(), line.isOptional(), line.notes(), order, List.of()))).toList();
        }, UnaryOperator.identity());
    }

    @Transactional
    public void removeLine(UUID recipeId, UUID lineId) {
        edit(recipeId, lines -> without(lines, lineId), UnaryOperator.identity());
    }

    @Transactional
    public void substitute(UUID recipeId, UUID lineId, UUID ingredientId) {
        edit(recipeId, lines -> {
            requireLine(lines, lineId);
            return lines.stream().map(l -> l.id().equals(lineId) ? new RecipeRequest.LineRequest(l.id(), ingredientId, l.section(),
                    l.quantity(), l.unitId(), l.optional(), l.quoteSelectable(), l.notes(), l.displayOrder(), l.extraAllergens()) : l).toList();
        }, UnaryOperator.identity());
    }

    @Transactional
    public void addFixedCost(UUID recipeId, RecipeFixedCostRequest fixed) {
        edit(recipeId, UnaryOperator.identity(), costs -> Stream.concat(costs.stream()
                        .filter(c -> !c.userFixedCostId().equals(fixed.userFixedCostId())),
                Stream.of(new RecipeRequest.FixedCostRequest(fixed.userFixedCostId(), fixed.timeInMinutes(), fixed.percentage()))).toList());
    }

    /** {@code fixedCostId} is the recipe fixed-cost row id. */
    @Transactional
    public void removeFixedCost(UUID recipeId, UUID fixedCostId) {
        RecipeAggregate recipe = owned(recipeId);
        UUID master = recipe.fixed().stream().filter(f -> f.id().equals(fixedCostId)).map(RecipeAggregate.Fixed::userFixedCostId)
                .findFirst().orElseThrow(() -> ApiException.notFound("kitchen.recipe.notFound"));
        edit(recipe, UnaryOperator.identity(), costs -> costs.stream().filter(c -> !c.userFixedCostId().equals(master)).toList());
    }

    @Transactional(readOnly = true)
    public CostPreview cost(UUID recipeId, BigDecimal targetYield) {
        return previews.preview(new CostPreviewRequest(recipeId, null, null, null, null, null,
                new RecipeConfiguration(null, null, targetYield)));
    }

    private void edit(UUID recipeId, UnaryOperator<List<RecipeRequest.LineRequest>> lines,
                      UnaryOperator<List<RecipeRequest.FixedCostRequest>> fixed) {
        edit(owned(recipeId), lines, fixed);
    }

    private void edit(RecipeAggregate recipe, UnaryOperator<List<RecipeRequest.LineRequest>> lines,
                      UnaryOperator<List<RecipeRequest.FixedCostRequest>> fixed) {
        RecipeAggregate.Head h = recipe.head();
        recipes.update(recipe.id(), new RecipeRequest(null, h.name(), h.categoryId(), h.difficulty(), h.description(), h.storageInstructions(),
                h.yieldQuantity(), h.yieldUnit(), h.prepMinutes(), h.bakeMinutes(), h.coolMinutes(), h.ovenTemperatureC(), h.shelfLifeDays(),
                h.processHtml(), h.targetMarginPercent(), h.wastePercent(), lines.apply(lines(recipe)), fixed.apply(fixed(recipe)), h.version()));
    }

    private RecipeAggregate owned(UUID recipeId) {
        UUID tenant = actors.require().id();
        return store.findVisible(recipeId, tenant).filter(r -> tenant.equals(r.head().ownerId()))
                .orElseThrow(() -> ApiException.notFound("kitchen.recipe.notFound"));
    }

    private static List<RecipeRequest.LineRequest> lines(RecipeAggregate recipe) {
        return recipe.lines().stream().map(l -> new RecipeRequest.LineRequest(l.id(), l.ingredientId(), l.section(), l.quantity(),
                l.unitId(), l.optional(), l.quoteSelectable(), l.notes(), l.displayOrder(),
                l.extraAllergens().stream().map(a -> new RecipeRequest.ExtraAllergenRequest(a.allergenId(), a.source())).toList())).toList();
    }

    private static List<RecipeRequest.FixedCostRequest> fixed(RecipeAggregate recipe) {
        return recipe.fixed().stream().map(f -> new RecipeRequest.FixedCostRequest(f.userFixedCostId(), f.minutes(), f.percentage())).toList();
    }

    private static List<RecipeRequest.LineRequest> without(List<RecipeRequest.LineRequest> lines, UUID lineId) {
        requireLine(lines, lineId);
        return lines.stream().filter(l -> !l.id().equals(lineId)).toList();
    }

    private static void requireLine(List<RecipeRequest.LineRequest> lines, UUID lineId) {
        if (lines.stream().noneMatch(l -> l.id().equals(lineId))) {
            throw ApiException.notFound("kitchen.recipe.lineNotFound");
        }
    }
}
