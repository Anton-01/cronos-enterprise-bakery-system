package com.ninsky.cronos.kitchen.recipe;

import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.Violations;
import com.ninsky.cronos.kitchen.costing.BaseQuantity;
import com.ninsky.cronos.kitchen.costing.CostContext;
import com.ninsky.cronos.kitchen.costing.CostEngine;
import com.ninsky.cronos.kitchen.costing.CostIngredient;
import com.ninsky.cronos.kitchen.costing.EffectivePrices;
import com.ninsky.cronos.kitchen.ingredient.IngredientQueries;
import com.ninsky.cronos.kitchen.shared.Numbers;
import com.ninsky.cronos.kitchen.unit.UnitCatalog;
import com.ninsky.cronos.kitchen.unit.UnitInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Prices a stored recipe under a {@link RecipeConfiguration} (configurator mode of §5.6, quotes §6.2):
 * validates exclusions/substitutions with field paths under {@code path}, applies them, runs the engine
 * and derives the configured product's allergens.
 */
@Component
@RequiredArgsConstructor
public class RecipeConfigurator {

    private final IngredientQueries ingredients;
    private final EffectivePrices prices;
    private final UnitCatalog units;
    private final CostContext costContext;
    private final CostEngine engine;

    /** Engine result, effective ingredient per line and the configured product's allergen ids. */
    public record Configured(CostEngine.Result result, Map<UUID, UUID> ingredientByLine, Set<Long> allergenIds, BigDecimal targetYield) {
    }

    /**
     * @param tenant pricing tenant (own prices first)
     * @param path   field prefix, e.g. {@code configuration} or {@code items[2].recipeConfiguration}
     */
    public Configured price(UUID tenant, RecipeAggregate recipe, RecipeConfiguration configuration, String path,
                            BigDecimal wastePercent, BigDecimal targetMarginPercent, Violations violations) {
        RecipeConfiguration config = configuration == null ? new RecipeConfiguration(null, null, null) : configuration;
        Set<UUID> excluded = new HashSet<>();
        for (int i = 0; i < config.excludedLineIds().size(); i++) {
            UUID lineId = config.excludedLineIds().get(i);
            Optional<RecipeAggregate.Line> line = lineId == null ? Optional.empty() : recipe.line(lineId);
            if (line.isEmpty() || !line.get().quoteSelectable()) {
                violations.invalid(path + ".excludedLineIds[" + i + "]", "kitchen.configuration.notSelectable");
            } else {
                excluded.add(lineId);
            }
        }
        BigDecimal targetYield = Optional.ofNullable(config.yieldQuantity()).orElse(recipe.head().yieldQuantity());
        violations.invalidIf(!Numbers.within(targetYield, "0.01", "100000", 2), path + ".yieldQuantity", "api.validation.range", "0.01", "100,000");

        Map<UUID, UUID> substituteByLine = new HashMap<>();
        Map<UUID, BigDecimal> ratioByLine = new HashMap<>();
        for (int i = 0; i < config.substitutions().size(); i++) {
            RecipeConfiguration.Substitution substitution = config.substitutions().get(i);
            String field = path + ".substitutions[" + i + "]";
            Optional<RecipeAggregate.Line> line = substitution.lineId() == null ? Optional.empty() : recipe.line(substitution.lineId());
            if (line.isEmpty()) {
                violations.invalid(field + ".lineId", "kitchen.configuration.unknownLine");
                continue;
            }
            Optional<IngredientQueries.SubstituteRow> row = ingredients.substitutes(tenant, line.get().ingredientId()).stream()
                    .filter(s -> s.substituteId().equals(substitution.ingredientId())).findFirst();
            if (row.isEmpty() || substituteByLine.containsKey(line.get().id())) {
                violations.add(ApiErrorCode.INVALID_SUBSTITUTION, field + ".ingredientId", "kitchen.configuration.invalidSubstitution");
                continue;
            }
            substituteByLine.put(line.get().id(), row.get().substituteId());
            ratioByLine.put(line.get().id(), row.get().ratio());
        }

        Set<UUID> ingredientIds = Stream.concat(recipe.lines().stream().map(RecipeAggregate.Line::ingredientId), substituteByLine.values().stream())
                .collect(Collectors.toSet());
        Map<UUID, CostIngredient> costed = prices.costIngredients(tenant, ingredientIds);
        Map<Long, UnitInfo> unitMap = units.snapshot().byId();
        for (Map.Entry<UUID, UUID> entry : substituteByLine.entrySet()) {
            RecipeAggregate.Line line = recipe.line(entry.getKey()).orElseThrow();
            CostIngredient substitute = costed.get(entry.getValue());
            UnitInfo unit = unitMap.get(line.unitId());
            if (substitute == null || unit == null || !BaseQuantity.compatible(unit.dimension(), substitute.baseDimension(), substitute.densityGPerMl())) {
                int index = config.substitutions().stream().map(RecipeConfiguration.Substitution::lineId).toList().indexOf(entry.getKey());
                violations.add(ApiErrorCode.UNIT_INCOMPATIBLE, path + ".substitutions[" + index + "].ingredientId", "kitchen.substitute.unitIncompatible");
            }
        }
        violations.throwIfAny();

        List<CostEngine.Line> lines = recipe.lines().stream().flatMap(line -> RecipeCostCalculator.line(line, costed, unitMap).stream().map(base -> {
            UUID substituteId = substituteByLine.get(line.id());
            return substituteId == null ? base : new CostEngine.Line(base.key(), base.ingredient(), base.quantity(), base.unit(), base.optional(),
                    costed.get(substituteId), ratioByLine.get(line.id()));
        })).toList();
        CostEngine.Request request = new CostEngine.Request(lines, recipe.fixed().stream().map(RecipeCostCalculator::fixed).toList(),
                recipe.head().yieldQuantity(), targetYield,
                Optional.ofNullable(wastePercent).orElse(recipe.head().wastePercent()),
                Optional.ofNullable(targetMarginPercent).orElse(recipe.head().targetMarginPercent()),
                excluded.stream().map(UUID::toString).collect(Collectors.toSet()), costContext.current().rules());
        CostEngine.Result result = engine.calculate(request);

        Map<UUID, UUID> ingredientByLine = recipe.lines().stream().collect(Collectors.toMap(RecipeAggregate.Line::id,
                l -> substituteByLine.getOrDefault(l.id(), l.ingredientId())));
        Map<UUID, List<Long>> allergens = ingredients.allergenIds(Set.copyOf(ingredientByLine.values()));
        Set<Long> allergenIds = RecipeAllergens.ids(recipe.lines(), l -> !excluded.contains(l.id()), l -> ingredientByLine.get(l.id()), allergens);
        return new Configured(result, ingredientByLine, allergenIds, targetYield);
    }
}
