package com.ninsky.cronos.kitchen.ingredient;

import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.infrastructure.web.paging.CatalogPage;
import com.ninsky.cronos.kitchen.allergen.AllergenCatalog;
import com.ninsky.cronos.kitchen.costing.PriceSource;
import com.ninsky.cronos.kitchen.shared.AllergenRef;
import com.ninsky.cronos.kitchen.shared.KitchenProperties;
import com.ninsky.cronos.kitchen.unit.UnitCatalog;
import com.ninsky.cronos.kitchen.unit.UnitInfo;
import com.ninsky.cronos.iam.shared.TenantTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Builds ingredient summaries/details: allergen refs from the cached catalog, base unit codes from the unit cache. */
@Component
@RequiredArgsConstructor
public class IngredientViews {

    private final IngredientQueries queries;
    private final AllergenCatalog allergens;
    private final UnitCatalog units;
    private final KitchenProperties properties;
    private final Clock clock;

    public LocalDate staleBefore() {
        return TenantTime.today(clock).minusDays(properties.stalePriceDays());
    }

    public CatalogPage<IngredientSummary> summaries(CatalogPage<IngredientQueries.Row> rows, UUID tenant, String language) {
        Map<UUID, List<Long>> declared = queries.allergenIds(rows.content().stream().map(IngredientQueries.Row::id).toList());
        AllergenCatalog.View view = allergens.view(tenant);
        LocalDate staleBefore = staleBefore();
        return rows.map(row -> summary(row, declared.getOrDefault(row.id(), List.of()), view, language, staleBefore));
    }

    public IngredientSummary summary(UUID tenant, String language, UUID id) {
        IngredientQueries.Row row = queries.find(tenant, language, id).orElseThrow(IngredientViews::notFound);
        return summary(row, queries.allergenIds(List.of(id)).getOrDefault(id, List.of()), allergens.view(tenant), language, staleBefore());
    }

    public IngredientDetail detail(UUID tenant, String language, UUID id) {
        IngredientQueries.Row row = queries.find(tenant, language, id).orElseThrow(IngredientViews::notFound);
        AllergenCatalog.View view = allergens.view(tenant);
        List<Long> declared = queries.allergenIds(List.of(id)).getOrDefault(id, List.of());
        IngredientQueries.Extra extra = queries.extra(id);
        Map<PriceSource, IngredientPrice> prices = queries.latestPrices(tenant, id);
        List<AllergenRef> suggested = view.refs(view.detector().detect(java.util.Arrays.asList(row.name(), extra.description()),
                Set.copyOf(declared)).stream().map(m -> m.allergenId()).toList(), language);
        return new IngredientDetail(summary(row, declared, view, language, staleBefore()), extra.description(), extra.brand(),
                extra.densityGPerMl(), prices.get(PriceSource.REFERENCE), prices.get(PriceSource.OWN),
                substitutes(tenant, language, id, declared, Set.of()), suggested, extra.createdAt(), extra.updatedAt(), extra.updatedBy(),
                extra.version());
    }

    /**
     * Substitutes declared on the ingredient, minus those containing any of {@code freeOf}; fewest
     * introduced allergens first, then name.
     */
    public List<SubstituteResponse> substitutes(UUID tenant, String language, UUID id, List<Long> originalAllergens, Set<Long> freeOf) {
        List<IngredientQueries.SubstituteRow> rows = queries.substitutes(tenant, id);
        if (rows.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = rows.stream().map(IngredientQueries.SubstituteRow::substituteId).toList();
        Map<UUID, IngredientQueries.Row> heads = queries.findAll(tenant, language, ids);
        Map<UUID, List<Long>> declared = queries.allergenIds(ids);
        AllergenCatalog.View view = allergens.view(tenant);
        Set<Long> original = Set.copyOf(originalAllergens);
        return rows.stream()
                .filter(r -> heads.containsKey(r.substituteId()))
                .filter(r -> declared.getOrDefault(r.substituteId(), List.of()).stream().noneMatch(freeOf::contains))
                .map(r -> {
                    Set<Long> own = Set.copyOf(declared.getOrDefault(r.substituteId(), List.of()));
                    Set<Long> removed = new HashSet<>(original);
                    removed.removeAll(own);
                    Set<Long> added = new HashSet<>(own);
                    added.removeAll(original);
                    return new SubstituteResponse(r.substituteId(), heads.get(r.substituteId()).name(), r.ratio(), r.notes(),
                            com.ninsky.cronos.kitchen.shared.Scope.of(r.ownerId()), view.refs(removed, language), view.refs(added, language));
                })
                .sorted(java.util.Comparator.comparingInt((SubstituteResponse s) -> s.introduces().size())
                        .thenComparing(SubstituteResponse::ingredientName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    private IngredientSummary summary(IngredientQueries.Row row, List<Long> declared, AllergenCatalog.View view, String language,
                                      LocalDate staleBefore) {
        String baseUnit = units.baseUnit(row.baseDimension()).map(UnitInfo::code).orElse(null);
        boolean stale = row.pricedAt() != null && row.pricedAt().isBefore(staleBefore);
        return new IngredientSummary(row.id(), row.code(), row.name(), row.categoryId(), row.categoryName(), row.scope(), row.baseDimension(),
                baseUnit, row.yieldPercent(), row.costPerBaseUnit(), row.priceSource(), row.pricedAt(), stale, view.refs(declared, language),
                row.usedInRecipes(), row.status());
    }

    static ApiException notFound() {
        return ApiException.notFound("kitchen.ingredient.notFound");
    }
}
