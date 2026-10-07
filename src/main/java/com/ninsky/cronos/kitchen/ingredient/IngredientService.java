package com.ninsky.cronos.kitchen.ingredient;

import com.ninsky.cronos.application.response.envelope.ApiWarning;
import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.domain.model.audit.AuditSeverity;
import com.ninsky.cronos.finance.shared.Changes;
import com.ninsky.cronos.iam.audit.AuditEvent;
import com.ninsky.cronos.iam.audit.AuditRecorder;
import com.ninsky.cronos.iam.audit.AuditTargets;
import com.ninsky.cronos.iam.permission.Permissions;
import com.ninsky.cronos.iam.shared.Actor;
import com.ninsky.cronos.iam.shared.ActorProvider;
import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.infrastructure.exception.Violations;
import com.ninsky.cronos.infrastructure.web.paging.CatalogPage;
import com.ninsky.cronos.infrastructure.web.paging.PageQuery;
import com.ninsky.cronos.kitchen.allergen.AllergenCatalog;
import com.ninsky.cronos.kitchen.costing.PurchaseCost;
import com.ninsky.cronos.kitchen.shared.ApiWarningCode;
import com.ninsky.cronos.kitchen.shared.KitchenCaches;
import com.ninsky.cronos.kitchen.shared.KitchenMessages;
import com.ninsky.cronos.kitchen.shared.KitchenStatus;
import com.ninsky.cronos.kitchen.shared.Scope;
import com.ninsky.cronos.kitchen.shared.StatusRequest;
import com.ninsky.cronos.kitchen.shared.Warned;
import com.ninsky.cronos.kitchen.unit.UnitInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Ingredient use cases (§4): SYSTEM ∪ own catalog, prices with ripple, substitutes and usage. */
@Service
@RequiredArgsConstructor
public class IngredientService {

    private static final List<String> LOCALES = List.of("es", "en");
    private static final BigDecimal JUMP_THRESHOLD = new BigDecimal("0.5");

    private final IngredientQueryCustomRepository queries;
    private final IngredientCustomRepository repository;
    private final IngredientValidator validator;
    private final IngredientViews views;
    private final IngredientRipple ripple;
    private final AllergenCatalog allergens;
    private final KitchenCaches caches;
    private final KitchenMessages messages;
    private final AuditRecorder audit;
    private final ActorProvider actors;
    private final Clock clock;

    @Transactional(readOnly = true)
    public CatalogPage<IngredientSummary> page(IngredientFilter filter, Integer page, Integer size, String sort) {
        UUID tenant = tenant();
        String language = KitchenMessages.language();
        PageQuery query = PageQuery.of(page, size, sort, IngredientQueryCustomRepository.SORTS, IngredientQueryCustomRepository.DEFAULT_SORT);
        return views.summaries(queries.page(tenant, language, filter, views.staleBefore(), query), tenant, language);
    }

    @Transactional(readOnly = true)
    public IngredientStats stats() {
        UUID tenant = tenant();
        return caches.get(KitchenCaches.STATS, KitchenCaches.statsKey("ingredients", tenant), () -> queries.stats(tenant, views.staleBefore()));
    }

    @Transactional(readOnly = true)
    public IngredientDetail detail(UUID id) {
        return views.detail(tenant(), KitchenMessages.language(), id);
    }

    @Transactional
    public IngredientDetail create(IngredientRequest request) {
        UUID tenant = tenant();
        String language = KitchenMessages.language();
        Violations violations = new Violations();
        validator.check(request, new IngredientValidator.Target(tenant, tenant, null, language), allergens.view(tenant), violations);
        violations.throwIfAny();

        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        String name = request.name().strip();
        IngredientCustomRepository.Head head = head(id, request.code(), tenant, request);
        repository.insert(head, tenant, now);
        repository.upsertTexts(id, LOCALES, name, blankToNull(request.description()));
        repository.replaceAllergens(id, new LinkedHashSet<>(request.allergenIds()));
        repository.replaceSubstitutes(id, tenant, request.substitutes());
        Optional.ofNullable(request.price()).ifPresent(price -> insertPrice(id, tenant, head, price, tenant, now));
        caches.evict(KitchenCaches.STATS, KitchenCaches.statsKey("ingredients", tenant));
        audit.record(AuditEvent.of(AuditAction.INGREDIENT_CREATED, AuditTargets.INGREDIENT, id, name)
                .changes(Changes.start().track("code", null, request.code()).track("name", null, name)
                        .track("baseDimension", null, request.baseDimension()).track("allergenIds", null, request.allergenIds()).build())
                .build());
        return views.detail(tenant, language, id);
    }

    @Transactional
    public IngredientDetail update(UUID id, IngredientRequest request) {
        if (request.version() == null) {
            throw ApiException.invalid("version", "api.validation.required");
        }
        Actor actor = actors.require();
        UUID tenant = actor.id();
        String language = KitchenMessages.language();
        IngredientQueryCustomRepository.Row current = editable(actor, language, id);
        boolean system = current.scope() == Scope.SYSTEM;

        Violations violations = new Violations();
        validator.check(request, new IngredientValidator.Target(tenant, current.ownerId(), current, language), allergens.view(tenant), violations);
        violations.throwIfAny();

        IngredientQueryCustomRepository.Extra extra = queries.extra(id);
        requireVersion(request.version(), extra.version());
        List<Long> previousAllergens = queries.allergenIds(List.of(id)).getOrDefault(id, List.of());
        String name = request.name().strip();
        Set<Long> nextAllergens = new LinkedHashSet<>(request.allergenIds());
        boolean costInputsChanged = !same(current.yieldPercent(), request.yieldPercent()) || !same(extra.densityGPerMl(), request.densityGPerMl());
        boolean allergensChanged = !Set.copyOf(previousAllergens).equals(nextAllergens);
        Changes changes = Changes.start()
                .track("name", current.name(), name)
                .track("description", extra.description(), blankToNull(request.description()))
                .track("categoryId", current.categoryId(), request.categoryId())
                .track("brand", extra.brand(), blankToNull(request.brand()))
                .track("baseDimension", current.baseDimension(), request.baseDimension())
                .track("yieldPercent", current.yieldPercent(), request.yieldPercent())
                .track("densityGPerMl", extra.densityGPerMl(), request.densityGPerMl())
                .track("allergenIds", List.copyOf(Set.copyOf(previousAllergens)), List.copyOf(nextAllergens))
                .track("substitutes", null, request.substitutes().isEmpty() ? null : request.substitutes().size());

        Instant now = clock.instant();
        IngredientCustomRepository.Head head = head(id, current.code(), current.ownerId(), request);
        if (!repository.update(head, request.version(), tenant, now)) {
            throw ApiException.concurrentModification();
        }
        repository.upsertTexts(id, system ? List.of(language) : LOCALES, name, blankToNull(request.description()));
        repository.replaceAllergens(id, nextAllergens);
        repository.replaceSubstitutes(id, current.ownerId(), request.substitutes());
        if (costInputsChanged) {
            repository.repriceLatest(id, tenant, now);
        }
        Optional.ofNullable(request.price()).ifPresent(price -> insertPrice(id, current.ownerId(), head, price, tenant, now));

        if (system && (costInputsChanged || request.price() != null)) {
            if (costInputsChanged) {
                ripple.everywhere(id, name, tenant);
            } else {
                ripple.referencePriceChanged(id, name, tenant);
            }
        } else if (costInputsChanged || request.price() != null) {
            ripple.recalculateForTenant(tenant, id, costInputsChanged ? IngredientRipple.INGREDIENT_CHANGED : IngredientRipple.PRICE_CHANGED,
                    name, tenant, now);
        }
        if (allergensChanged) {
            ripple.allergensChanged(id, name, tenant, now);
        }
        evictStats(system, tenant);
        audit.record(AuditEvent.of(AuditAction.INGREDIENT_UPDATED, AuditTargets.INGREDIENT, id, name)
                .severity(system ? AuditSeverity.WARNING : AuditSeverity.NOTICE)
                .changes(changes.build())
                .build());
        return views.detail(tenant, language, id);
    }

    @Transactional
    public IngredientDetail changeStatus(UUID id, StatusRequest request) {
        Actor actor = actors.require();
        String language = KitchenMessages.language();
        IngredientQueryCustomRepository.Row current = editable(actor, language, id);
        IngredientQueryCustomRepository.Extra extra = queries.extra(id);
        requireVersion(request.version(), extra.version());
        if (current.status() != request.status()) {
            if (!repository.changeStatus(id, request.version(), request.status(), actor.id(), clock.instant())) {
                throw ApiException.concurrentModification();
            }
            if (request.status() == KitchenStatus.INACTIVE) {
                ripple.deactivated(id);
            }
            evictStats(current.scope() == Scope.SYSTEM, actor.id());
            audit.record(AuditEvent.of(AuditAction.INGREDIENT_STATUS_CHANGED, AuditTargets.INGREDIENT, id, current.name())
                    .changes(Changes.start().track("status", current.status(), request.status()).build())
                    .params(Map.of("detail", request.status().name()))
                    .build());
        }
        return views.detail(actor.id(), language, id);
    }

    @Transactional
    public void delete(UUID id) {
        UUID tenant = tenant();
        IngredientQueryCustomRepository.Row current = queries.find(tenant, KitchenMessages.language(), id).orElseThrow(IngredientViews::notFound);
        if (current.scope() == Scope.SYSTEM) {
            throw ApiException.of(ApiErrorCode.SYSTEM_RESOURCE_CONFLICT, null, "kitchen.system.readOnly");
        }
        if (queries.usedAnywhere(id)) {
            throw ApiException.of(ApiErrorCode.RESOURCE_IN_USE, null, "kitchen.ingredient.inUse", current.usedInRecipes());
        }
        repository.delete(id);
        evictStats(false, tenant);
        audit.record(AuditEvent.of(AuditAction.INGREDIENT_DELETED, AuditTargets.INGREDIENT, id, current.name())
                .changes(Changes.start().track("code", current.code(), null).build())
                .build());
    }

    /** §4.4/§4.5: an own price (also on SYSTEM rows), ripple to the caller's recipes and open quotes. */
    @Transactional
    public Warned<PriceImpact> registerPrice(UUID id, IngredientPriceRequest request) {
        UUID tenant = tenant();
        String language = KitchenMessages.language();
        IngredientQueryCustomRepository.Row current = queries.find(tenant, language, id).orElseThrow(IngredientViews::notFound);
        IngredientQueryCustomRepository.Extra extra = queries.extra(id);
        Violations violations = new Violations();
        Optional<UnitInfo> unit = validator.checkPrice(request, "", current.baseDimension(), extra.densityGPerMl(), violations);
        violations.throwIfAny();

        BigDecimal cost = PurchaseCost.costPerBaseUnit(request.price(), request.purchaseQuantity(), unit.orElseThrow(), current.baseDimension(),
                extra.densityGPerMl(), current.yieldPercent());
        List<ApiWarning> warnings = new ArrayList<>();
        BigDecimal previous = current.costPerBaseUnit();
        boolean jump = previous != null && previous.signum() > 0
                && cost.subtract(previous).abs().divide(previous, 10, RoundingMode.HALF_EVEN).compareTo(JUMP_THRESHOLD) > 0;
        if (jump) {
            BigDecimal percent = cost.subtract(previous).multiply(BigDecimal.valueOf(100)).divide(previous, 1, RoundingMode.HALF_EVEN);
            warnings.add(new ApiWarning(ApiWarningCode.PRICE_JUMP.name(), "price", messages.get("kitchen.warning.priceJump", percent)));
        }
        Instant now = clock.instant();
        repository.insertPrice(new IngredientCustomRepository.NewPrice(UUID.randomUUID(), id, tenant, request.purchaseQuantity(),
                request.purchaseUnitId(), request.price(), request.currency().strip().toUpperCase(java.util.Locale.ROOT),
                blankToNull(request.supplier()), request.pricedAt(), cost), tenant, now);

        IngredientRipple.Result result = ripple.recalculateForTenant(tenant, id, IngredientRipple.PRICE_CHANGED, current.name(), tenant, now);
        List<PriceImpact.BelowMargin> belowMargin = ripple.belowMargin(tenant, result.outcomes());
        evictStats(false, tenant);
        audit.record(AuditEvent.of(AuditAction.INGREDIENT_PRICE_REGISTERED, AuditTargets.INGREDIENT, id, current.name())
                .severity(jump ? AuditSeverity.WARNING : AuditSeverity.NOTICE)
                .changes(Changes.start().track("costPerBaseUnit", previous, cost).track("price", null, request.price()).build())
                .params(Map.of("detail", request.price().toPlainString() + " " + request.currency(), "priceJump", jump,
                        "recipesAffected", result.recipesAffected()))
                .build());
        PriceImpact impact = new PriceImpact(views.summary(tenant, language, id), result.recipesAffected(), result.quotesFlagged(),
                belowMargin, result.status());
        return new Warned<>(impact, warnings);
    }

    @Transactional(readOnly = true)
    public CatalogPage<PriceHistoryEntry> prices(UUID id, Integer page, Integer size) {
        UUID tenant = tenant();
        queries.find(tenant, KitchenMessages.language(), id).orElseThrow(IngredientViews::notFound);
        return queries.history(tenant, id, PageQuery.of(page, size, null, Map.of("pricedAt", "p.priced_at"), "pricedAt,desc"));
    }

    @Transactional(readOnly = true)
    public List<SubstituteResponse> substitutes(UUID id, List<Long> freeOfAllergenIds) {
        UUID tenant = tenant();
        String language = KitchenMessages.language();
        queries.find(tenant, language, id).orElseThrow(IngredientViews::notFound);
        List<Long> declared = queries.allergenIds(List.of(id)).getOrDefault(id, List.of());
        return views.substitutes(tenant, language, id, declared,
                freeOfAllergenIds == null ? Set.of() : Set.copyOf(freeOfAllergenIds.stream().filter(Objects::nonNull).toList()));
    }

    @Transactional(readOnly = true)
    public List<IngredientUsage> usage(UUID id) {
        UUID tenant = tenant();
        queries.find(tenant, KitchenMessages.language(), id).orElseThrow(IngredientViews::notFound);
        return queries.usage(tenant, id);
    }

    /** Visible row the actor may edit: own rows with INGREDIENT.UPDATE, SYSTEM rows with CATALOG.INGREDIENT.MANAGE (K7). */
    private IngredientQueryCustomRepository.Row editable(Actor actor, String language, UUID id) {
        IngredientQueryCustomRepository.Row row = queries.find(actor.id(), language, id).orElseThrow(IngredientViews::notFound);
        if (row.scope() == Scope.SYSTEM && !actor.holds(Permissions.CATALOG_INGREDIENT_MANAGE)) {
            throw ApiException.of(ApiErrorCode.SYSTEM_RESOURCE_CONFLICT, null, "kitchen.system.readOnly");
        }
        if (row.scope() == Scope.USER && !actor.holds(Permissions.INGREDIENT_UPDATE)) {
            throw ApiException.of(ApiErrorCode.ACCESS_DENIED, null, "api.accessDenied");
        }
        return row;
    }

    private void insertPrice(UUID id, UUID ownerId, IngredientCustomRepository.Head head, IngredientPriceRequest price, UUID actor, Instant now) {
        UnitInfo unit = validator.checkPrice(price, "price.", head.baseDimension(), head.densityGPerMl(), new Violations()).orElseThrow();
        BigDecimal cost = PurchaseCost.costPerBaseUnit(price.price(), price.purchaseQuantity(), unit, head.baseDimension(),
                head.densityGPerMl(), head.yieldPercent());
        repository.insertPrice(new IngredientCustomRepository.NewPrice(UUID.randomUUID(), id, ownerId, price.purchaseQuantity(), unit.id(),
                price.price(), price.currency().strip().toUpperCase(java.util.Locale.ROOT), blankToNull(price.supplier()),
                price.pricedAt(), cost), actor, now);
    }

    private void evictStats(boolean system, UUID tenant) {
        if (system) {
            caches.clear(KitchenCaches.STATS);
        } else {
            caches.evict(KitchenCaches.STATS, KitchenCaches.statsKey("ingredients", tenant));
        }
    }

    private static IngredientCustomRepository.Head head(UUID id, String code, UUID ownerId, IngredientRequest request) {
        return new IngredientCustomRepository.Head(id, code, ownerId, request.categoryId(), request.baseDimension(), request.yieldPercent(),
                request.densityGPerMl(), blankToNull(request.brand()));
    }

    private static void requireVersion(Long requested, long current) {
        if (requested == null || requested != current) {
            throw ApiException.concurrentModification();
        }
    }

    private static boolean same(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private UUID tenant() {
        return actors.require().id();
    }
}
