package com.ninsky.cronos.kitchen.recipe;

import com.ninsky.cronos.application.response.envelope.ApiWarning;
import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.finance.shared.Changes;
import com.ninsky.cronos.iam.audit.AuditEvent;
import com.ninsky.cronos.iam.audit.AuditRecorder;
import com.ninsky.cronos.iam.audit.AuditTargets;
import com.ninsky.cronos.iam.shared.ActorProvider;
import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.infrastructure.exception.Violations;
import com.ninsky.cronos.infrastructure.web.paging.CatalogPage;
import com.ninsky.cronos.infrastructure.web.paging.PageQuery;
import com.ninsky.cronos.kitchen.allergen.AllergenCatalog;
import com.ninsky.cronos.kitchen.costing.CostEngine;
import com.ninsky.cronos.kitchen.shared.ApiWarningCode;
import com.ninsky.cronos.kitchen.shared.KitchenCaches;
import com.ninsky.cronos.kitchen.shared.KitchenMessages;
import com.ninsky.cronos.kitchen.shared.Warned;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Recipe aggregate use cases (§5): save with revision, lifecycle, duplicate, soft delete, recalculation. */
@Service
@RequiredArgsConstructor
public class RecipeService {

    private static final String COPY_SUFFIX = "_COPY";

    private final RecipeStore store;
    private final RecipeQueries queries;
    private final RecipeValidator validator;
    private final RecipeViews views;
    private final RecipeCosting costing;
    private final RecipeRevisions revisions;
    private final AllergenCatalog allergens;
    private final KitchenCaches caches;
    private final KitchenMessages messages;
    private final AuditRecorder audit;
    private final ActorProvider actors;
    private final Clock clock;

    @Transactional(readOnly = true)
    public CatalogPage<RecipeSummary> page(RecipeFilter filter, Integer page, Integer size, String sort) {
        UUID tenant = tenant();
        PageQuery query = PageQuery.of(page, size, sort, RecipeQueries.SORTS, RecipeQueries.DEFAULT_SORT);
        return views.summaries(queries.page(tenant, filter, query), tenant, KitchenMessages.language());
    }

    @Transactional(readOnly = true)
    public RecipeStats stats() {
        UUID tenant = tenant();
        return caches.get(KitchenCaches.STATS, KitchenCaches.statsKey("recipes", tenant), () -> queries.stats(tenant));
    }

    @Transactional(readOnly = true)
    public List<RecipeOption> simple(String search) {
        return queries.simple(tenant(), search);
    }

    @Transactional(readOnly = true)
    public RecipeDetail detail(UUID id) {
        UUID tenant = tenant();
        return views.detail(visible(id, tenant), tenant, KitchenMessages.language());
    }

    @Transactional(readOnly = true)
    public CatalogPage<RecipeRevision> history(UUID id, Integer page, Integer size) {
        visible(id, tenant());
        return revisions.page(id, page, size);
    }

    @Transactional
    public RecipeDetail create(RecipeRequest request) {
        UUID tenant = tenant();
        String language = KitchenMessages.language();
        RecipeValidator.Draft draft = validator.validate(request, new RecipeValidator.Context(tenant, language, allergens.view(tenant), null));
        Instant now = clock.instant();
        RecipeAggregate recipe = insert(UUID.randomUUID(), request.code(), tenant, draft, now);
        CostEngine.Result result = costing.evaluate(recipe);
        costing.persistAt(recipe, null, result, 1, tenant, now, RecipeRevisions.Reason.of("kitchen.revision.created"),
                Map.of("lines", Map.of("added", recipe.lines().size())));
        evictStats(tenant);
        audit.record(AuditEvent.of(AuditAction.RECIPE_CREATED, AuditTargets.RECIPE, recipe.id(), draft.name())
                .changes(Changes.start().track("code", null, request.code()).track("name", null, draft.name())
                        .track("lines", null, recipe.lines().size()).build())
                .build());
        return views.detail(store.findVisible(recipe.id(), tenant).orElseThrow(), tenant, language);
    }

    @Transactional
    public RecipeDetail update(UUID id, RecipeRequest request) {
        if (request.version() == null) {
            throw ApiException.invalid("version", "api.validation.required");
        }
        UUID tenant = tenant();
        String language = KitchenMessages.language();
        RecipeAggregate current = owned(id, tenant);
        requireVersion(request.version(), current.head().version());
        RecipeValidator.Draft draft = validator.validate(request, new RecipeValidator.Context(tenant, language, allergens.view(tenant), current));

        Instant now = clock.instant();
        long version = current.head().version() + 1;
        RecipeAggregate.Head head = head(id, current.head().code(), tenant, draft, current.head().status(), current.head().createdAt(),
                current.head().createdBy(), now, version, current.head().cost());
        if (!store.update(head, current.head().version())) {
            throw ApiException.concurrentModification();
        }
        store.replaceLines(id, draft.lines());
        store.replaceFixed(id, draft.fixed());
        RecipeAggregate next = new RecipeAggregate(head, draft.lines(), draft.fixed());
        Map<String, Object> changes = RecipeDiff.of(current, next);
        costing.persistAt(next, current.head().cost(), costing.evaluate(next), version, tenant, now,
                RecipeRevisions.Reason.of("kitchen.revision.updated"), changes);
        evictStats(tenant);
        audit.record(AuditEvent.of(AuditAction.RECIPE_UPDATED, AuditTargets.RECIPE, id, draft.name()).changes(changes).build());
        return views.detail(store.findVisible(id, tenant).orElseThrow(), tenant, language);
    }

    /** Lifecycle (§5.2); publishing allows unpriced lines / empty process but reports them as warnings. */
    @Transactional
    public Warned<RecipeDetail> changeStatus(UUID id, RecipeStatusRequest request) {
        UUID tenant = tenant();
        RecipeAggregate current = owned(id, tenant);
        requireVersion(request.version(), current.head().version());
        RecipeStatus from = current.head().status();
        List<ApiWarning> warnings = new ArrayList<>();
        if (from != request.status()) {
            if (!from.canMoveTo(request.status())) {
                throw ApiException.of(ApiErrorCode.INVALID_STATE_TRANSITION, "status", "kitchen.recipe.transition", from, request.status());
            }
            if (request.status() == RecipeStatus.ACTIVE) {
                if (current.lines().isEmpty() || current.head().yieldQuantity().signum() <= 0) {
                    throw ApiException.of(ApiErrorCode.INVALID_STATE_TRANSITION, "status", "kitchen.recipe.publishRequirements");
                }
                if (current.head().cost().unpricedLines() > 0) {
                    warnings.add(new ApiWarning(ApiWarningCode.UNPRICED_LINES.name(), "lines",
                            messages.get("kitchen.warning.unpricedLines", current.head().cost().unpricedLines())));
                }
                if (current.head().processHtml() == null) {
                    warnings.add(new ApiWarning(ApiWarningCode.EMPTY_PROCESS.name(), "processHtml", messages.get("kitchen.warning.emptyProcess")));
                }
            }
            Instant now = clock.instant();
            if (!store.changeStatus(id, request.version(), request.status(), tenant, now)) {
                throw ApiException.concurrentModification();
            }
            Map<String, Object> changes = Changes.start().track("status", from, request.status()).build();
            revisions.write(id, current.head().version() + 1, tenant, now,
                    RecipeRevisions.Reason.of("kitchen.revision.statusChanged", request.status().name()), changes, current.head().cost().costPerUnit());
            evictStats(tenant);
            audit.record(AuditEvent.of(AuditAction.RECIPE_STATUS_CHANGED, AuditTargets.RECIPE, id, current.head().name())
                    .changes(changes).params(Map.of("detail", request.status().name())).build());
        }
        return new Warned<>(views.detail(store.findVisible(id, tenant).orElseThrow(), tenant, KitchenMessages.language()), warnings);
    }

    /** New DRAFT copy of an own or SYSTEM recipe (lines, fixed costs, process; never files, history or shares). */
    @Transactional
    public RecipeDetail duplicate(UUID id, DuplicateRequest request) {
        UUID tenant = tenant();
        RecipeAggregate source = visible(id, tenant);
        String name = request == null || request.name() == null ? null : request.name().strip();
        Violations violations = new Violations();
        if (name == null || name.length() < 3 || name.length() > 120) {
            violations.invalid("name", "api.validation.length", 3, 120);
        } else if (store.nameTaken(tenant, name, null)) {
            violations.add(ApiErrorCode.DUPLICATE_RESOURCE, "name", "kitchen.name.duplicate");
        }
        violations.throwIfAny();

        RecipeAggregate.Head s = source.head();
        RecipeValidator.Draft draft = new RecipeValidator.Draft(name, s.categoryId(), s.difficulty(), s.description(),
                s.storageInstructions(), s.yieldQuantity(), s.yieldUnit(), s.prepMinutes(), s.bakeMinutes(), s.coolMinutes(), s.ovenTemperatureC(),
                s.shelfLifeDays(), s.processHtml(), s.targetMarginPercent(), s.wastePercent(),
                source.lines().stream().map(l -> new RecipeAggregate.Line(UUID.randomUUID(), l.ingredientId(), l.section(), l.quantity(),
                        l.unitId(), l.optional(), l.quoteSelectable(), l.notes(), l.displayOrder(), null, l.extraAllergens())).toList(),
                s.system() ? List.of() : source.fixed().stream().map(f -> new RecipeAggregate.Fixed(UUID.randomUUID(), f.userFixedCostId(),
                        f.name(), f.method(), f.defaultAmount(), f.masterPercentage(), f.minutes(), f.percentage(), null)).toList());
        Instant now = clock.instant();
        RecipeAggregate copy = insert(UUID.randomUUID(), copyCode(tenant, s.code()), tenant, draft, now);
        costing.persistAt(copy, null, costing.evaluate(copy), 1, tenant, now,
                RecipeRevisions.Reason.of(s.system() ? "kitchen.revision.fromLibrary" : "kitchen.revision.duplicated", s.name()), Map.of());
        evictStats(tenant);
        audit.record(AuditEvent.of(AuditAction.RECIPE_DUPLICATED, AuditTargets.RECIPE, copy.id(), name)
                .params(Map.of("detail", s.name(), "sourceId", s.id().toString()))
                .build());
        return views.detail(store.findVisible(copy.id(), tenant).orElseThrow(), tenant, KitchenMessages.language());
    }

    /** Soft delete: quotes keep their snapshot; files are purged later. */
    @Transactional
    public void delete(UUID id) {
        UUID tenant = tenant();
        RecipeAggregate current = owned(id, tenant);
        store.softDelete(id, tenant, clock.instant());
        evictStats(tenant);
        audit.record(AuditEvent.of(AuditAction.RECIPE_DELETED, AuditTargets.RECIPE, id, current.head().name())
                .changes(Changes.start().track("code", current.head().code(), null).build())
                .build());
    }

    @Transactional
    public RecipeDetail recalculate(UUID id) {
        UUID tenant = tenant();
        RecipeAggregate current = owned(id, tenant);
        costing.recalculateLoaded(List.of(current), tenant, clock.instant(), RecipeRevisions.Reason.of("kitchen.revision.recalculated"));
        evictStats(tenant);
        return views.detail(store.findVisible(id, tenant).orElseThrow(), tenant, KitchenMessages.language());
    }

    private RecipeAggregate insert(UUID id, String code, UUID owner, RecipeValidator.Draft draft, Instant now) {
        RecipeAggregate.Head head = head(id, code, owner, draft, RecipeStatus.DRAFT, now, owner, now, 0, null);
        store.insert(head);
        store.replaceLines(id, draft.lines());
        store.replaceFixed(id, draft.fixed());
        return new RecipeAggregate(head, draft.lines(), draft.fixed());
    }

    private static RecipeAggregate.Head head(UUID id, String code, UUID owner, RecipeValidator.Draft d, RecipeStatus status, Instant createdAt,
                                             UUID createdBy, Instant now, long version, RecipeAggregate.Cost cost) {
        RecipeAggregate.Cost effective = cost != null ? cost
                : new RecipeAggregate.Cost(null, null, null, null, null, null, 0, com.ninsky.cronos.kitchen.costing.CostStatus.INCOMPLETE, null);
        return new RecipeAggregate.Head(id, code, owner, d.name(), d.categoryId(), d.difficulty(), d.description(), d.processHtml(),
                d.storageInstructions(), d.shelfLifeDays(), d.prepMinutes(), d.bakeMinutes(), d.coolMinutes(), d.ovenTemperatureC(),
                d.yieldQuantity(), d.yieldUnit(), status, d.targetMarginPercent(), d.wastePercent(), effective, createdAt, createdBy, now,
                owner, version);
    }

    /** {@code {code}_COPY}, then {@code _COPY_2…}, within 50 chars and unique for the tenant. */
    private String copyCode(UUID tenant, String code) {
        for (int n = 1; ; n++) {
            String suffix = n == 1 ? COPY_SUFFIX : COPY_SUFFIX + "_" + n;
            String candidate = code.substring(0, Math.min(code.length(), 50 - suffix.length())) + suffix;
            if (!store.codeTaken(tenant, candidate, null)) {
                return candidate;
            }
        }
    }

    private RecipeAggregate visible(UUID id, UUID tenant) {
        return store.findVisible(id, tenant).orElseThrow(RecipeService::notFound);
    }

    /** Own recipe locked for writing; SYSTEM library rows are read-only (K7). */
    private RecipeAggregate owned(UUID id, UUID tenant) {
        RecipeAggregate recipe = visible(id, tenant);
        if (recipe.head().system()) {
            throw ApiException.of(ApiErrorCode.SYSTEM_RESOURCE_CONFLICT, null, "kitchen.system.readOnly");
        }
        return store.lockOwned(id, tenant).orElseThrow(RecipeService::notFound);
    }

    private void evictStats(UUID tenant) {
        caches.evict(KitchenCaches.STATS, KitchenCaches.statsKey("recipes", tenant));
    }

    private static void requireVersion(Long requested, long current) {
        if (requested == null || requested != current) {
            throw ApiException.concurrentModification();
        }
    }

    private static ApiException notFound() {
        return ApiException.notFound("kitchen.recipe.notFound");
    }

    private UUID tenant() {
        return actors.require().id();
    }
}
