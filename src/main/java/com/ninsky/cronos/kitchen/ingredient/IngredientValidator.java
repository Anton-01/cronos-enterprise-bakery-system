package com.ninsky.cronos.kitchen.ingredient;

import com.ninsky.cronos.iam.shared.TenantTime;
import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.Violations;
import com.ninsky.cronos.kitchen.allergen.AllergenCatalog;
import com.ninsky.cronos.kitchen.costing.BaseQuantity;
import com.ninsky.cronos.kitchen.costing.CostContext;
import com.ninsky.cronos.kitchen.shared.KitchenCategoryCustomRepository;
import com.ninsky.cronos.kitchen.shared.Dimension;
import com.ninsky.cronos.kitchen.shared.KitchenStatus;
import com.ninsky.cronos.kitchen.shared.Numbers;
import com.ninsky.cronos.kitchen.shared.Scope;
import com.ninsky.cronos.kitchen.unit.UnitCatalog;
import com.ninsky.cronos.kitchen.unit.UnitInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Every rule of §4.3/§4.4, collected at once with exact field paths. */
@Component
@RequiredArgsConstructor
public class IngredientValidator {

    static final Pattern CODE = Pattern.compile("^[A-Z][A-Z0-9_]{1,49}$");
    static final int MAX_SUBSTITUTES = 10;
    private static final Set<Dimension> DENSITY_BRIDGE = Set.of(Dimension.MASS, Dimension.VOLUME);

    private final IngredientQueryCustomRepository queries;
    private final KitchenCategoryCustomRepository categories;
    private final UnitCatalog units;
    private final CostContext costContext;
    private final Clock clock;

    /** What the request is checked against: the row being edited (null on create). */
    public record Target(UUID tenant, UUID ownerId, IngredientQueryCustomRepository.Row current, String language) {
        boolean creating() {
            return current == null;
        }

        UUID selfId() {
            return current == null ? null : current.id();
        }
    }

    public void check(IngredientRequest request, Target target, AllergenCatalog.View allergens, Violations violations) {
        if (target.creating()) {
            if (request.code() == null || !CODE.matcher(request.code()).matches()) {
                violations.invalid("code", "api.validation.pattern");
            } else if (queries.codeTaken(target.ownerId(), request.code())) {
                violations.add(ApiErrorCode.DUPLICATE_RESOURCE, "code", "kitchen.code.duplicate");
            }
        } else {
            violations.invalidIf(request.code() != null && !request.code().equals(target.current().code()), "code", "kitchen.code.immutable");
        }
        checkName(request.name(), target, violations);

        if (request.categoryId() == null) {
            violations.invalid("categoryId", "api.validation.required");
        } else if (!categories.usable(target.tenant(), request.categoryId(), "INGREDIENT")) {
            violations.invalid("categoryId", "kitchen.category.invalid");
        }
        violations.invalidIf(request.description() != null && request.description().length() > 500, "description", "api.validation.maxLength", 500);
        violations.invalidIf(request.brand() != null && request.brand().length() > 80, "brand", "api.validation.maxLength", 80);

        Dimension dimension = request.baseDimension();
        if (dimension == null) {
            violations.invalid("baseDimension", "api.validation.required");
        } else if (!target.creating() && dimension != target.current().baseDimension() && queries.usedAnywhere(target.selfId())) {
            violations.add(ApiErrorCode.RESOURCE_IN_USE, "baseDimension", "kitchen.ingredient.dimensionLocked");
        }
        violations.invalidIf(!Numbers.within(request.yieldPercent(), "1", "100", 1), "yieldPercent", "api.validation.range", 1, 100);
        BigDecimal density = request.densityGPerMl();
        if (density != null) {
            violations.invalidIf(!Numbers.within(density, "0.1", "3", 3), "densityGPerMl", "api.validation.range", "0.1", 3);
            violations.invalidIf(dimension == Dimension.COUNT, "densityGPerMl", "kitchen.ingredient.density.count");
        }
        checkAllergens(request.allergenIds(), allergens, violations);
        checkSubstitutes(request, target, violations);
        if (request.price() != null && dimension != null) {
            checkPrice(request.price(), "price.", dimension, density, violations);
        }
    }

    /** §4.4 rules; {@code prefix} is "" for {@code POST /prices} and "price." when nested. */
    public Optional<UnitInfo> checkPrice(IngredientPriceRequest price, String prefix, Dimension dimension, BigDecimal density,
                                         Violations violations) {
        violations.invalidIf(!Numbers.positive(price.purchaseQuantity(), "1000000", 4), prefix + "purchaseQuantity",
                "kitchen.validation.quantity");
        Optional<UnitInfo> unit = units.find(price.purchaseUnitId()).filter(UnitInfo::active);
        if (unit.isEmpty()) {
            violations.invalid(prefix + "purchaseUnitId", price.purchaseUnitId() == null ? "api.validation.required" : "kitchen.unit.invalid");
        } else if (!BaseQuantity.compatible(unit.get().dimension(), dimension, density)) {
            violations.add(ApiErrorCode.UNIT_INCOMPATIBLE, prefix + "purchaseUnitId", "kitchen.unit.incompatible", unit.get().code(), dimension);
        }
        CostContext.Money money = costContext.current();
        int decimals = Math.max(money.rules().decimals(), 2);
        violations.invalidIf(!Numbers.within(price.price(), "0.01", "10000000", decimals), prefix + "price", "api.validation.range",
                "0.01", "10,000,000");
        if (price.currency() == null || price.currency().isBlank()) {
            violations.invalid(prefix + "currency", "api.validation.required");
        } else if (!money.currency().equalsIgnoreCase(price.currency().strip())) {
            violations.add(ApiErrorCode.CURRENCY_NOT_SUPPORTED, prefix + "currency", "kitchen.price.currency", money.currency());
        }
        violations.invalidIf(price.supplier() != null && price.supplier().length() > 120, prefix + "supplier", "api.validation.maxLength", 120);
        LocalDate today = TenantTime.today(clock);
        if (price.pricedAt() == null) {
            violations.invalid(prefix + "pricedAt", "api.validation.required");
        } else {
            violations.invalidIf(price.pricedAt().isAfter(today) || price.pricedAt().isBefore(today.minusYears(5)), prefix + "pricedAt",
                    "kitchen.price.pricedAt");
        }
        return unit;
    }

    private void checkName(String raw, Target target, Violations violations) {
        String name = raw == null ? "" : raw.strip();
        if (name.length() < 2 || name.length() > 120) {
            violations.invalid("name", "api.validation.length", 2, 120);
            return;
        }
        queries.nameOwner(target.tenant(), name, target.selfId()).ifPresent(scope ->
                violations.add(ApiErrorCode.DUPLICATE_RESOURCE, "name",
                        scope == Scope.SYSTEM ? "kitchen.ingredient.name.shadowsSystem" : "kitchen.name.duplicate"));
    }

    private static void checkAllergens(List<Long> ids, AllergenCatalog.View allergens, Violations violations) {
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < ids.size(); i++) {
            Long id = ids.get(i);
            String field = "allergenIds[" + i + "]";
            if (id == null || allergens.find(id).filter(e -> e.status() == KitchenStatus.ACTIVE).isEmpty()) {
                violations.invalid(field, "kitchen.allergen.invalid");
            } else if (!seen.add(id)) {
                violations.invalid(field, "api.validation.duplicateEntry");
            }
        }
    }

    private void checkSubstitutes(IngredientRequest request, Target target, Violations violations) {
        List<IngredientRequest.Substitute> substitutes = request.substitutes();
        violations.invalidIf(substitutes.size() > MAX_SUBSTITUTES, "substitutes", "api.validation.listSize", 0, MAX_SUBSTITUTES);
        Map<UUID, IngredientQueryCustomRepository.Row> rows = queries.findAll(target.tenant(), target.language(),
                substitutes.stream().map(IngredientRequest.Substitute::ingredientId).filter(Objects::nonNull).toList());
        Map<UUID, BigDecimal> densities = queries.densities(rows.keySet());
        Set<UUID> seen = new HashSet<>();
        for (int i = 0; i < substitutes.size(); i++) {
            IngredientRequest.Substitute s = substitutes.get(i);
            String prefix = "substitutes[" + i + "].";
            IngredientQueryCustomRepository.Row row = s.ingredientId() == null ? null : rows.get(s.ingredientId());
            if (row == null || row.status() != KitchenStatus.ACTIVE) {
                violations.invalid(prefix + "ingredientId", "kitchen.ingredient.invalid");
            } else if (Objects.equals(row.id(), target.selfId())) {
                violations.invalid(prefix + "ingredientId", "kitchen.substitute.self");
            } else if (!seen.add(row.id())) {
                violations.invalid(prefix + "ingredientId", "api.validation.duplicateEntry");
            } else if (request.baseDimension() != null
                    && !compatible(request.baseDimension(), request.densityGPerMl(), row.baseDimension(), densities.get(row.id()))) {
                violations.add(ApiErrorCode.UNIT_INCOMPATIBLE, prefix + "ingredientId", "kitchen.substitute.incompatible", row.name());
            }
            violations.invalidIf(!Numbers.within(s.ratio(), "0.01", "10", 3), prefix + "ratio", "api.validation.range", "0.01", 10);
            violations.invalidIf(s.notes() != null && s.notes().length() > 200, prefix + "notes", "api.validation.maxLength", 200);
        }
    }

    static boolean compatible(Dimension a, BigDecimal densityA, Dimension b, BigDecimal densityB) {
        return a == b || (DENSITY_BRIDGE.contains(a) && DENSITY_BRIDGE.contains(b) && densityA != null && densityB != null);
    }
}
