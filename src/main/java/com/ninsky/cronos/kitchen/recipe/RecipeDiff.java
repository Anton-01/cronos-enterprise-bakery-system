package com.ninsky.cronos.kitchen.recipe;

import com.ninsky.cronos.finance.shared.Changes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** §5.8 revision diff: head fields, lines by id (added/removed/changed fields) and fixed-cost count. */
final class RecipeDiff {

    private RecipeDiff() {
    }

    static Map<String, Object> of(RecipeAggregate before, RecipeAggregate after) {
        RecipeAggregate.Head a = before.head();
        RecipeAggregate.Head b = after.head();
        Map<String, Object> changes = new LinkedHashMap<>(Changes.start()
                .track("name", a.name(), b.name())
                .track("categoryId", a.categoryId(), b.categoryId())
                .track("difficulty", a.difficulty(), b.difficulty())
                .track("description", a.description(), b.description())
                .track("processHtml", digest(a.processHtml()), digest(b.processHtml()))
                .track("storageInstructions", a.storageInstructions(), b.storageInstructions())
                .track("shelfLifeDays", a.shelfLifeDays(), b.shelfLifeDays())
                .track("prepMinutes", a.prepMinutes(), b.prepMinutes())
                .track("bakeMinutes", a.bakeMinutes(), b.bakeMinutes())
                .track("coolMinutes", a.coolMinutes(), b.coolMinutes())
                .track("ovenTemperatureC", a.ovenTemperatureC(), b.ovenTemperatureC())
                .track("yieldQuantity", a.yieldQuantity(), b.yieldQuantity())
                .track("yieldUnit", a.yieldUnit(), b.yieldUnit())
                .track("targetMarginPercent", a.targetMarginPercent(), b.targetMarginPercent())
                .track("pricingMethod", a.pricingMethod(), b.pricingMethod())
                .track("wastePercent", a.wastePercent(), b.wastePercent())
                .build());
        Map<String, Object> lines = lines(before.lines(), after.lines());
        if (!lines.isEmpty()) {
            changes.put("lines", lines);
        }
        if (!fixedRows(before).equals(fixedRows(after))) {
            changes.put("fixedCosts", Map.of("from", before.fixed().size(), "to", after.fixed().size()));
        }
        return changes;
    }

    private static Map<String, Object> lines(List<RecipeAggregate.Line> before, List<RecipeAggregate.Line> after) {
        Map<UUID, RecipeAggregate.Line> old = before.stream().collect(Collectors.toMap(RecipeAggregate.Line::id, Function.identity()));
        Map<UUID, RecipeAggregate.Line> next = after.stream().collect(Collectors.toMap(RecipeAggregate.Line::id, Function.identity()));
        List<String> added = after.stream().filter(l -> !old.containsKey(l.id())).map(l -> l.ingredientId().toString()).toList();
        List<String> removed = before.stream().filter(l -> !next.containsKey(l.id())).map(l -> l.ingredientId().toString()).toList();
        List<Map<String, Object>> changed = new ArrayList<>();
        after.stream().filter(l -> old.containsKey(l.id())).forEach(l -> {
            RecipeAggregate.Line o = old.get(l.id());
            Map<String, Object> fields = new LinkedHashMap<>(Changes.start()
                    .track("ingredientId", o.ingredientId(), l.ingredientId())
                    .track("quantity", o.quantity(), l.quantity())
                    .track("unitId", o.unitId(), l.unitId())
                    .track("section", o.section(), l.section())
                    .track("optional", o.optional(), l.optional())
                    .track("quoteSelectable", o.quoteSelectable(), l.quoteSelectable())
                    .track("notes", o.notes(), l.notes())
                    .track("extraAllergens", ids(o), ids(l))
                    .build());
            if (!fields.isEmpty()) {
                fields.put("id", l.id().toString());
                changed.add(fields);
            }
        });
        Map<String, Object> result = new LinkedHashMap<>();
        if (!added.isEmpty()) {
            result.put("added", added);
        }
        if (!removed.isEmpty()) {
            result.put("removed", removed);
        }
        if (!changed.isEmpty()) {
            result.put("changed", changed);
        }
        return result;
    }

    /** What a fixed-cost row contributes: master + its per-recipe inputs (row ids are regenerated on every save). */
    private static Set<List<Object>> fixedRows(RecipeAggregate recipe) {
        return recipe.fixed().stream()
                .map(f -> java.util.Arrays.<Object>asList(f.userFixedCostId(), f.minutes(), strip(f.percentage()), strip(f.quantity())))
                .collect(Collectors.toSet());
    }

    private static java.math.BigDecimal strip(java.math.BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros();
    }

    private static Set<Long> ids(RecipeAggregate.Line line) {
        return line.extraAllergens().stream().map(RecipeAggregate.ExtraAllergen::allergenId).collect(Collectors.toCollection(java.util.TreeSet::new));
    }

    /** Process HTML is diffed by length only (the full text lives in the recipe). */
    private static Integer digest(String html) {
        return html == null ? null : html.length();
    }
}
