package com.ninsky.cronos.kitchen.recipe;

import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.Violations;
import com.ninsky.cronos.kitchen.allergen.AllergenCatalog;
import com.ninsky.cronos.kitchen.costing.BaseQuantity;
import com.ninsky.cronos.kitchen.costing.FixedCostMethod;
import com.ninsky.cronos.kitchen.ingredient.IngredientQueries;
import com.ninsky.cronos.kitchen.shared.CategoryLookup;
import com.ninsky.cronos.kitchen.shared.KitchenStatus;
import com.ninsky.cronos.kitchen.shared.Numbers;
import com.ninsky.cronos.kitchen.shared.TextNormalizer;
import com.ninsky.cronos.kitchen.unit.UnitCatalog;
import com.ninsky.cronos.kitchen.unit.UnitInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Every rule of §5.3 with exact field paths ({@code lines[3].quantity}), collected at once. */
@Component
@RequiredArgsConstructor
public class RecipeValidator {

    static final Pattern CODE = Pattern.compile("^[A-Z][A-Z0-9_]{1,49}$");
    static final int MAX_LINES = 150;
    static final int MAX_FIXED = 20;
    static final BigDecimal DEFAULT_MARGIN = BigDecimal.valueOf(60);
    static final BigDecimal DEFAULT_WASTE = BigDecimal.valueOf(3);

    private final RecipeStore store;
    private final IngredientQueries ingredients;
    private final CategoryLookup categories;
    private final FixedCostLookup fixedCosts;
    private final UnitCatalog units;

    /** Lookups shared by the line checks. */
    public record Context(UUID tenant, String language, AllergenCatalog.View allergens, RecipeAggregate current) {
    }

    /** A validated aggregate ready to persist (ids assigned, HTML sanitised, defaults applied). */
    public record Draft(String name, Long categoryId, Difficulty difficulty, String description, String storageInstructions,
                        BigDecimal yieldQuantity, String yieldUnit, Integer prepMinutes, Integer bakeMinutes, Integer coolMinutes,
                        Integer ovenTemperatureC, Integer shelfLifeDays, String processHtml, BigDecimal targetMarginPercent,
                        BigDecimal wastePercent, List<RecipeAggregate.Line> lines, List<RecipeAggregate.Fixed> fixed) {
    }

    public Draft validate(RecipeRequest request, Context context) {
        Violations violations = new Violations();
        RecipeAggregate current = context.current();
        if (current == null) {
            if (request.code() == null || !CODE.matcher(request.code()).matches()) {
                violations.invalid("code", "api.validation.pattern");
            } else if (store.codeTaken(context.tenant(), request.code(), null)) {
                violations.add(ApiErrorCode.DUPLICATE_RESOURCE, "code", "kitchen.code.duplicate");
            }
        } else {
            violations.invalidIf(request.code() != null && !request.code().equals(current.head().code()), "code", "kitchen.code.immutable");
        }
        String name = strip(request.name());
        if (name == null || name.length() < 3 || name.length() > 120) {
            violations.invalid("name", "api.validation.length", 3, 120);
        } else if (store.nameTaken(context.tenant(), name, current == null ? null : current.id())) {
            violations.add(ApiErrorCode.DUPLICATE_RESOURCE, "name", "kitchen.name.duplicate");
        }
        violations.invalidIf(request.categoryId() != null && !categories.usable(context.tenant(), request.categoryId(), "PRODUCT"),
                "categoryId", "kitchen.category.invalid");
        maxLength(violations, "description", request.description(), 1000);
        maxLength(violations, "storageInstructions", request.storageInstructions(), 1000);
        violations.invalidIf(!Numbers.within(request.yieldQuantity(), "0.01", "100000", 2), "yieldQuantity", "api.validation.range", "0.01", "100,000");
        String yieldUnit = strip(request.yieldUnit());
        violations.invalidIf(yieldUnit == null || yieldUnit.length() > 30, "yieldUnit", "api.validation.length", 1, 30);
        minutes(violations, "prepMinutes", request.prepMinutes());
        minutes(violations, "bakeMinutes", request.bakeMinutes());
        minutes(violations, "coolMinutes", request.coolMinutes());
        violations.invalidIf(!Numbers.within(request.ovenTemperatureC(), 30, 320), "ovenTemperatureC", "api.validation.range", 30, 320);
        violations.invalidIf(!Numbers.within(request.shelfLifeDays(), 0, 730), "shelfLifeDays", "api.validation.range", 0, 730);
        String process = ProcessHtmlSanitizer.sanitize(request.processHtml());
        violations.invalidIf(process != null && process.length() > ProcessHtmlSanitizer.MAX_LENGTH, "processHtml", "api.validation.maxLength",
                ProcessHtmlSanitizer.MAX_LENGTH);
        BigDecimal margin = Optional.ofNullable(request.targetMarginPercent()).orElse(DEFAULT_MARGIN);
        BigDecimal waste = Optional.ofNullable(request.wastePercent()).orElse(DEFAULT_WASTE);
        violations.invalidIf(!Numbers.within(margin, "0", "1000", 2), "targetMarginPercent", "api.validation.range", 0, 1000);
        violations.invalidIf(!Numbers.within(waste, "0", "50", 2), "wastePercent", "api.validation.range", 0, 50);

        violations.invalidIf(request.lines().isEmpty() || request.lines().size() > MAX_LINES, "lines", "api.validation.listSize", 1, MAX_LINES);
        List<RecipeAggregate.Line> lines = lines(request.lines(), "lines", context, violations);
        List<RecipeAggregate.Fixed> fixed = fixed(request.fixedCosts(), "fixedCosts", context.tenant(), violations);
        violations.throwIfAny();

        return new Draft(name, request.categoryId(), Optional.ofNullable(request.difficulty()).orElse(Difficulty.EASY),
                strip(request.description()), strip(request.storageInstructions()), request.yieldQuantity(), yieldUnit, request.prepMinutes(),
                request.bakeMinutes(), request.coolMinutes(), request.ovenTemperatureC(), request.shelfLifeDays(), process, margin, waste,
                lines, fixed);
    }

    /** Line rules; also used by the editor-mode cost preview. New lines get fresh ids. */
    public List<RecipeAggregate.Line> lines(List<RecipeRequest.LineRequest> requests, String path, Context context, Violations violations) {
        RecipeAggregate current = context.current();
        List<UUID> ids = requests.stream().map(RecipeRequest.LineRequest::ingredientId).filter(Objects::nonNull).distinct().toList();
        Map<UUID, IngredientQueries.Row> rows = ingredients.findAll(context.tenant(), context.language(), ids);
        Map<UUID, BigDecimal> densities = ingredients.densities(rows.keySet());
        Map<UUID, List<Long>> declared = ingredients.allergenIds(rows.keySet());
        Set<String> seen = new HashSet<>();
        Set<UUID> lineIds = new HashSet<>();
        List<RecipeAggregate.Line> lines = new ArrayList<>();

        for (int i = 0; i < requests.size(); i++) {
            RecipeRequest.LineRequest line = requests.get(i);
            String prefix = path + "[" + i + "].";
            Optional<RecipeAggregate.Line> existing = Optional.ofNullable(line.id()).flatMap(id -> current == null ? Optional.empty() : current.line(id));
            if (line.id() != null && (existing.isEmpty() || !lineIds.add(line.id()))) {
                violations.invalid(prefix + "id", "kitchen.recipe.line.foreign");
            }
            IngredientQueries.Row ingredient = line.ingredientId() == null ? null : rows.get(line.ingredientId());
            boolean keptInactive = ingredient != null && existing.map(e -> e.ingredientId().equals(line.ingredientId())).orElse(false);
            if (ingredient == null || (ingredient.status() != KitchenStatus.ACTIVE && !keptInactive)) {
                violations.invalid(prefix + "ingredientId", line.ingredientId() == null ? "api.validation.required" : "kitchen.ingredient.invalid");
            }
            violations.invalidIf(!Numbers.positive(line.quantity(), "1000000", 4), prefix + "quantity", "kitchen.validation.quantity");
            Optional<UnitInfo> unit = units.find(line.unitId());
            if (unit.isEmpty() || !unit.get().active()) {
                violations.invalid(prefix + "unitId", line.unitId() == null ? "api.validation.required" : "kitchen.unit.invalid");
            } else if (ingredient != null && !BaseQuantity.compatible(unit.get().dimension(), ingredient.baseDimension(), densities.get(ingredient.id()))) {
                violations.add(ApiErrorCode.UNIT_INCOMPATIBLE, prefix + "unitId", "kitchen.unit.incompatible", unit.get().code(), ingredient.name());
            }
            String section = strip(line.section());
            maxLength(violations, prefix + "section", section, 40);
            maxLength(violations, prefix + "notes", line.notes(), 200);
            if (ingredient != null && !seen.add(TextNormalizer.fold(section == null ? "" : section) + '|' + ingredient.id())) {
                violations.invalid(prefix + "ingredientId", "kitchen.recipe.line.duplicate", ingredient.name());
            }
            Set<Long> fromIngredient = ingredient == null ? Set.of() : Set.copyOf(declared.getOrDefault(ingredient.id(), List.of()));
            List<RecipeAggregate.ExtraAllergen> extras = extras(line.extraAllergens(), prefix + "extraAllergens", fromIngredient,
                    context.allergens(), violations);
            int order = line.displayOrder() == null ? i : line.displayOrder();
            lines.add(new RecipeAggregate.Line(existing.isPresent() ? line.id() : UUID.randomUUID(), line.ingredientId(), section,
                    line.quantity(), unit.map(UnitInfo::id).orElse(0L), line.optional(), line.optional() || line.quoteSelectable(),
                    strip(line.notes()), order, null, extras));
        }
        return lines;
    }

    private static List<RecipeAggregate.ExtraAllergen> extras(List<RecipeRequest.ExtraAllergenRequest> requests, String path,
                                                              Set<Long> fromIngredient, AllergenCatalog.View allergens, Violations violations) {
        Map<Long, RecipeAggregate.ExtraAllergen> extras = new LinkedHashMap<>();
        for (int j = 0; j < requests.size(); j++) {
            RecipeRequest.ExtraAllergenRequest extra = requests.get(j);
            String prefix = path + "[" + j + "].";
            boolean active = extra.allergenId() != null && allergens.find(extra.allergenId()).filter(e -> e.status() == KitchenStatus.ACTIVE).isPresent();
            if (!active) {
                violations.invalid(prefix + "allergenId", "kitchen.allergen.invalid");
            } else if (extra.source() == null || extra.source() == AllergenSource.INGREDIENT) {
                violations.invalid(prefix + "source", "kitchen.recipe.allergenSource");
            } else if (extras.containsKey(extra.allergenId())) {
                violations.invalid(prefix + "allergenId", "api.validation.duplicateEntry");
            } else if (!fromIngredient.contains(extra.allergenId())) {
                extras.put(extra.allergenId(), new RecipeAggregate.ExtraAllergen(extra.allergenId(), extra.source()));
            }
        }
        return List.copyOf(extras.values());
    }

    /** Fixed-cost rules; also used by the editor-mode cost preview. */
    public List<RecipeAggregate.Fixed> fixed(List<RecipeRequest.FixedCostRequest> requests, String path, UUID tenant, Violations violations) {
        violations.invalidIf(requests.size() > MAX_FIXED, path, "api.validation.listSize", 0, MAX_FIXED);
        Map<UUID, FixedCostLookup.Master> masters = fixedCosts.find(tenant,
                requests.stream().map(RecipeRequest.FixedCostRequest::userFixedCostId).filter(Objects::nonNull).toList());
        Set<UUID> seen = new HashSet<>();
        List<RecipeAggregate.Fixed> fixed = new ArrayList<>();
        for (int i = 0; i < requests.size(); i++) {
            RecipeRequest.FixedCostRequest request = requests.get(i);
            String prefix = path + "[" + i + "].";
            FixedCostLookup.Master master = request.userFixedCostId() == null ? null : masters.get(request.userFixedCostId());
            if (master == null || !master.active()) {
                violations.invalid(prefix + "userFixedCostId", "kitchen.fixedCost.invalid");
                continue;
            }
            if (!seen.add(master.id())) {
                violations.invalid(prefix + "userFixedCostId", "api.validation.duplicateEntry");
            }
            if (master.method() == FixedCostMethod.HOURLY_RATE) {
                violations.invalidIf(request.minutes() == null || request.minutes() < 1 || request.minutes() > 10_080, prefix + "minutes",
                        "api.validation.range", 1, 10_080);
                violations.invalidIf(request.percentage() != null, prefix + "percentage", "kitchen.fixedCost.notApplicable");
            } else if (master.method() == FixedCostMethod.PERCENTAGE) {
                violations.invalidIf(request.percentage() != null && !Numbers.within(request.percentage(), "0", "100", 2), prefix + "percentage",
                        "api.validation.range", 0, 100);
                violations.invalidIf(request.minutes() != null, prefix + "minutes", "kitchen.fixedCost.notApplicable");
            } else {
                violations.invalidIf(request.minutes() != null, prefix + "minutes", "kitchen.fixedCost.notApplicable");
                violations.invalidIf(request.percentage() != null, prefix + "percentage", "kitchen.fixedCost.notApplicable");
            }
            fixed.add(new RecipeAggregate.Fixed(UUID.randomUUID(), master.id(), master.name(), master.method(), master.defaultAmount(),
                    master.percentage(), request.minutes(), request.percentage(), null));
        }
        return fixed;
    }

    private static void minutes(Violations violations, String field, Integer value) {
        violations.invalidIf(!Numbers.within(value, 0, 10_080), field, "api.validation.range", 0, 10_080);
    }

    private static void maxLength(Violations violations, String field, String value, int max) {
        violations.invalidIf(value != null && value.length() > max, field, "api.validation.maxLength", max);
    }

    private static String strip(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
