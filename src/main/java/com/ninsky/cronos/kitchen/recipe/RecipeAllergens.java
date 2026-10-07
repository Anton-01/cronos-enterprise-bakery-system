package com.ninsky.cronos.kitchen.recipe;

import com.ninsky.cronos.kitchen.allergen.AllergenCatalog;
import com.ninsky.cronos.kitchen.shared.AllergenRef;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/** §5.4 derivation: line allergens = ingredient's (INGREDIENT) ∪ extras (DETECTED|MANUAL); contains vs may contain. */
public final class RecipeAllergens {

    private RecipeAllergens() {
    }

    /** Line allergens in provenance order (ingredient first), localised. */
    public static List<RecipeDetail.LineAllergen> ofLine(RecipeAggregate.Line line, Map<UUID, List<Long>> declared,
                                                         AllergenCatalog.View view, String language) {
        Map<Long, AllergenSource> sources = new LinkedHashMap<>();
        declared.getOrDefault(line.ingredientId(), List.of()).forEach(id -> sources.putIfAbsent(id, AllergenSource.INGREDIENT));
        line.extraAllergens().forEach(e -> sources.putIfAbsent(e.allergenId(), e.source()));
        return sources.entrySet().stream()
                .flatMap(e -> view.find(e.getKey()).stream()
                        .map(a -> new RecipeDetail.LineAllergen(a.id(), a.code(), a.name(language), e.getValue())))
                .toList();
    }

    /** Allergen ids of the lines matching {@code include}; {@code ingredientOf} allows substitutions. */
    public static Set<Long> ids(Collection<RecipeAggregate.Line> lines, Predicate<RecipeAggregate.Line> include,
                                java.util.function.Function<RecipeAggregate.Line, UUID> ingredientOf, Map<UUID, List<Long>> declared) {
        Set<Long> ids = new LinkedHashSet<>();
        lines.stream().filter(include).forEach(line -> {
            ids.addAll(declared.getOrDefault(ingredientOf.apply(line), List.of()));
            line.extraAllergens().forEach(e -> ids.add(e.allergenId()));
        });
        return ids;
    }

    public static Set<Long> contains(Collection<RecipeAggregate.Line> lines, Map<UUID, List<Long>> declared) {
        return ids(lines, l -> !l.optional(), RecipeAggregate.Line::ingredientId, declared);
    }

    public static Set<Long> mayContain(Collection<RecipeAggregate.Line> lines, Map<UUID, List<Long>> declared) {
        Set<Long> may = ids(lines, RecipeAggregate.Line::optional, RecipeAggregate.Line::ingredientId, declared);
        may.removeAll(contains(lines, declared));
        return may;
    }

    public static List<AllergenRef> refs(Set<Long> ids, AllergenCatalog.View view, String language) {
        return view.refs(ids, language);
    }
}
