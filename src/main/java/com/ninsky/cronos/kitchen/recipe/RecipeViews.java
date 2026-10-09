package com.ninsky.cronos.kitchen.recipe;

import com.ninsky.cronos.finance.shared.UserRefCustomRepository;
import com.ninsky.cronos.infrastructure.web.paging.CatalogPage;
import com.ninsky.cronos.kitchen.allergen.AllergenCatalog;
import com.ninsky.cronos.kitchen.costing.CostContext;
import com.ninsky.cronos.kitchen.ingredient.IngredientQueryCustomRepository;
import com.ninsky.cronos.kitchen.recipe.file.RecipeFileService;
import com.ninsky.cronos.kitchen.recipe.file.RecipeFileCustomRepository;
import com.ninsky.cronos.kitchen.shared.KitchenCategoryCustomRepository;
import com.ninsky.cronos.kitchen.shared.KitchenProperties;
import com.ninsky.cronos.kitchen.shared.Scope;
import com.ninsky.cronos.kitchen.unit.UnitCatalog;
import com.ninsky.cronos.kitchen.unit.UnitInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Assembles recipe summaries and details from batched lookups (no N+1). */
@Component
@RequiredArgsConstructor
public class RecipeViews {

    private final RecipeQueryCustomRepository queries;
    private final IngredientQueryCustomRepository ingredients;
    private final AllergenCatalog allergens;
    private final KitchenCategoryCustomRepository categories;
    private final UnitCatalog units;
    private final RecipeFileCustomRepository files;
    private final RecipeFileService fileService;
    private final CostContext costContext;
    private final UserRefCustomRepository userRefs;
    private final KitchenProperties properties;

    public CatalogPage<RecipeSummary> summaries(CatalogPage<UUID> ids, UUID tenant, String language) {
        List<RecipeAggregate.Head> heads = queries.heads(ids.content());
        List<UUID> recipeIds = heads.stream().map(RecipeAggregate.Head::id).toList();
        Map<UUID, Set<Long>> contained = queries.containedAllergens(recipeIds);
        Map<Long, String> categoryNames = categories.names(heads.stream().map(RecipeAggregate.Head::categoryId).filter(Objects::nonNull).toList());
        Map<UUID, String> covers = files.coverKeys(recipeIds);
        AllergenCatalog.View view = allergens.view(tenant);
        List<RecipeSummary> rows = heads.stream()
                .map(h -> summary(h, contained.getOrDefault(h.id(), Set.of()), categoryNames, covers.get(h.id()), view, language))
                .toList();
        return new CatalogPage<>(rows, ids.pageNumber(), ids.pageSize(), ids.totalElements(), ids.totalPages(), ids.last());
    }

    public RecipeDetail detail(RecipeAggregate recipe, UUID tenant, String language) {
        RecipeAggregate.Head head = recipe.head();
        List<UUID> ingredientIds = recipe.lines().stream().map(RecipeAggregate.Line::ingredientId).distinct().toList();
        Map<UUID, IngredientQueryCustomRepository.Row> rows = ingredients.findAll(tenant, language, ingredientIds);
        Map<UUID, List<Long>> declared = ingredients.allergenIds(ingredientIds);
        Map<Long, UnitInfo> unitMap = units.snapshot().byId();
        AllergenCatalog.View view = allergens.view(tenant);

        List<RecipeDetail.Line> lines = recipe.lines().stream().map(l -> new RecipeDetail.Line(l.id(), l.ingredientId(),
                Optional.ofNullable(rows.get(l.ingredientId())).map(IngredientQueryCustomRepository.Row::name).orElse(null), l.section(), l.quantity(),
                l.unitId(), Optional.ofNullable(unitMap.get(l.unitId())).map(UnitInfo::code).orElse(null), l.optional(), l.quoteSelectable(),
                l.notes(), RecipeAllergens.ofLine(l, declared, view, language), l.lineCost(), l.displayOrder())).toList();
        List<RecipeDetail.FixedCost> fixed = recipe.fixed().stream().map(f -> new RecipeDetail.FixedCost(f.id(), f.userFixedCostId(), f.name(),
                f.method(), f.minutes(), f.percentage(), f.quantity(), f.cost())).toList();
        List<RecipeFileCustomRepository.Row> fileRows = files.list(head.id());
        String cover = fileRows.stream().filter(RecipeFileCustomRepository.Row::cover).findFirst()
                .map(RecipeFileCustomRepository.Row::coverKey).orElse(null);
        Map<Long, String> categoryNames = categories.names(head.categoryId() == null ? List.of() : List.of(head.categoryId()));
        RecipeAggregate.Cost cost = head.cost();

        return new RecipeDetail(summary(head, RecipeAllergens.contains(recipe.lines(), declared), categoryNames, cover, view, language),
                head.description(), head.processHtml(), head.storageInstructions(), head.shelfLifeDays(), head.prepMinutes(), head.bakeMinutes(),
                head.coolMinutes(), head.ovenTemperatureC(), head.wastePercent(), lines, fixed,
                fileRows.stream().map(fileService::response).toList(),
                new RecipeDetail.Cost(cost.ingredientsCost(), cost.wasteCost(), cost.fixedCosts(), cost.totalCost(), cost.costPerUnit(),
                        cost.suggestedUnitPrice(), head.pricingMethod(), costContext.current().currency(), cost.status(), cost.unpricedLines(),
                        cost.calculatedAt()),
                view.refs(RecipeAllergens.mayContain(recipe.lines(), declared), language), head.createdAt(),
                userRefs.find(head.createdBy()).orElse(null), userRefs.find(head.updatedBy()).orElse(null), head.version());
    }

    private RecipeSummary summary(RecipeAggregate.Head head, Set<Long> contained, Map<Long, String> categoryNames, String coverKey,
                                  AllergenCatalog.View view, String language) {
        RecipeAggregate.Cost cost = head.cost();
        return new RecipeSummary(head.id(), head.code(), head.name(), head.categoryId(),
                head.categoryId() == null ? null : categoryNames.get(head.categoryId()), head.difficulty(), head.yieldQuantity(),
                head.yieldUnit(), head.status(), Scope.of(head.ownerId()), view.refs(contained, language),
                fileService.signed(coverKey, properties.signedUrlMinutes()), cost.costPerUnit(), cost.suggestedUnitPrice(),
                head.targetMarginPercent(), head.pricingMethod(), cost.status(), cost.calculatedAt(), head.totalMinutes(), head.updatedAt());
    }
}
