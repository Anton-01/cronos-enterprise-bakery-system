package com.ninsky.cronos.finance.taxrate;

import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.domain.model.audit.AuditSeverity;
import com.ninsky.cronos.finance.shared.Changes;
import com.ninsky.cronos.finance.shared.FinanceLocks;
import com.ninsky.cronos.finance.shared.FinanceSettingsCache;
import com.ninsky.cronos.finance.shared.FinanceStatus;
import com.ninsky.cronos.finance.shared.StatusRequest;
import com.ninsky.cronos.finance.shared.VersionRequest;
import com.ninsky.cronos.iam.audit.AuditEvent;
import com.ninsky.cronos.iam.audit.AuditRecorder;
import com.ninsky.cronos.iam.audit.AuditTargets;
import com.ninsky.cronos.iam.shared.ActorProvider;
import com.ninsky.cronos.iam.shared.TenantTime;
import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.infrastructure.exception.Violations;
import com.ninsky.cronos.infrastructure.web.paging.CatalogPage;
import com.ninsky.cronos.infrastructure.web.paging.PageQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/** IVA rate catalog use cases (spec §10): SAT rules, in-use immutability, single default, audit (N4). */
@Service
@RequiredArgsConstructor
public class TaxRateService {

    private static final long NO_ID = -1L;

    private final TaxRateRepository repository;
    private final TaxRateQueries queries;
    private final FinanceLocks locks;
    private final FinanceSettingsCache settingsCache;
    private final AuditRecorder audit;
    private final ActorProvider actors;
    private final Clock clock;

    @Transactional(readOnly = true)
    public CatalogPage<TaxRateResponse> page(String search, FinanceStatus status, Integer page, Integer size, String sort) {
        return queries.page(search, status, PageQuery.of(page, size, sort, TaxRateQueries.SORTS, TaxRateQueries.DEFAULT_SORT));
    }

    /** ACTIVE and currently valid in the tenant timezone, default first. */
    @Transactional(readOnly = true)
    public List<TaxRateOption> catalog() {
        return repository.findSelectable(FinanceStatus.ACTIVE, TenantTime.today(clock)).stream().map(TaxRateOption::of).toList();
    }

    @Transactional
    public TaxRateResponse create(TaxRateRequest request) {
        TaxRateDraft draft = TaxRateDraft.from(request);
        Violations violations = rules(draft);
        checkUniqueness(draft, NO_ID, violations);
        violations.throwIfAny();

        TaxRateEntity saved = repository.saveAndFlush(TaxRateEntity.create(draft, actorId(), now()));
        audit.record(AuditEvent.of(AuditAction.TAX_RATE_CREATED, AuditTargets.TAX_RATE, saved.getId(), label(saved))
                .changes(diff(null, saved.draft()).build())
                .build());
        return view(saved.getId());
    }

    @Transactional
    public TaxRateResponse update(long id, TaxRateRequest request) {
        if (request.version() == null) {
            throw ApiException.invalid("version", "api.validation.required");
        }
        TaxRateEntity entity = load(id);
        FinanceLocks.requireVersion(request.version(), entity.getVersion());
        TaxRateDraft current = entity.draft();
        TaxRateDraft draft = TaxRateDraft.from(request);

        Violations violations = rules(draft);
        checkUniqueness(draft, id, violations);
        List<String> frozen = draft.immutableFieldsChangedFrom(current);
        if (!frozen.isEmpty() && queries.inUse(id)) {
            frozen.forEach(field -> violations.add(ApiErrorCode.RESOURCE_IN_USE, field, "finance.taxRate.immutableInUse"));
        }
        violations.throwIfAny();

        Changes changes = diff(current, draft);
        if (changes.isEmpty()) {
            return view(id);
        }
        entity.update(draft, actorId(), now());
        repository.saveAndFlush(entity);
        audit.record(AuditEvent.of(AuditAction.TAX_RATE_UPDATED, AuditTargets.TAX_RATE, id, label(entity)).changes(changes.build()).build());
        if (entity.isDefault()) {
            settingsCache.evict();
        }
        return view(id);
    }

    @Transactional
    public TaxRateResponse changeStatus(long id, StatusRequest request) {
        TaxRateEntity entity = load(id);
        FinanceLocks.requireVersion(request.version(), entity.getVersion());
        if (entity.getStatus() == request.status()) {
            return view(id);
        }
        if (entity.isDefault()) {
            throw ApiException.of(ApiErrorCode.DEFAULT_LOCKED, "status", "finance.taxRate.defaultLocked.deactivate");
        }
        FinanceStatus previous = entity.getStatus();
        entity.changeStatus(request.status(), actorId(), now());
        repository.saveAndFlush(entity);
        audit.record(AuditEvent.of(AuditAction.TAX_RATE_STATUS_CHANGED, AuditTargets.TAX_RATE, id, label(entity))
                .changes(Changes.start().track("status", previous, request.status()).build())
                .params(Map.of("detail", request.status().name()))
                .build());
        return view(id);
    }

    @Transactional
    public TaxRateResponse makeDefault(long id, VersionRequest request) {
        locks.lockDefaults();
        TaxRateEntity target = load(id);
        FinanceLocks.requireVersion(request.version(), target.getVersion());
        if (target.isDefault()) {
            return view(id);
        }
        if (!target.isActive() || !target.isValidOn(TenantTime.today(clock))) {
            throw ApiException.of(ApiErrorCode.INVALID_STATE_TRANSITION, null, "finance.taxRate.default.notSelectable");
        }
        UUID actor = actorId();
        Instant now = now();
        Optional<TaxRateEntity> previous = repository.findByIsDefaultTrue();
        previous.ifPresent(old -> {
            old.markDefault(false, actor, now);
            repository.saveAndFlush(old);
        });
        target.markDefault(true, actor, now);
        repository.saveAndFlush(target);
        audit.record(AuditEvent.of(AuditAction.FINANCE_DEFAULT_CHANGED, AuditTargets.TAX_RATE, id, label(target))
                .severity(AuditSeverity.WARNING)
                .changes(Changes.start().track("defaultTaxRate", previous.map(TaxRateEntity::getCode).orElse(null), target.getCode()).build())
                .params(Map.of("detail", target.getCode()))
                .build());
        settingsCache.evict();
        return view(id);
    }

    @Transactional
    public void delete(long id) {
        TaxRateEntity entity = load(id);
        if (entity.isDefault()) {
            throw ApiException.of(ApiErrorCode.DEFAULT_LOCKED, null, "finance.taxRate.defaultLocked.delete");
        }
        if (queries.inUse(id)) {
            throw ApiException.of(ApiErrorCode.RESOURCE_IN_USE, null, "finance.taxRate.inUse.delete");
        }
        repository.delete(entity);
        repository.flush();
        audit.record(AuditEvent.of(AuditAction.TAX_RATE_DELETED, AuditTargets.TAX_RATE, id, label(entity))
                .changes(diff(entity.draft(), null).build())
                .build());
    }

    private static Violations rules(TaxRateDraft draft) {
        Violations violations = new Violations();
        TaxRateRules.check(draft.factorType(), draft.ratePercent(), draft.validFrom(), draft.validTo())
                .forEach(issue -> violations.invalid(issue.field(), issue.messageKey(), issue.args().toArray()));
        return violations;
    }

    private void checkUniqueness(TaxRateDraft draft, long excludeId, Violations violations) {
        if (repository.existsByCodeAndIdNot(draft.code(), excludeId)) {
            violations.add(ApiErrorCode.DUPLICATE_RESOURCE, "code", "finance.taxRate.code.duplicate");
        }
        if (repository.existsByNameIgnoreCaseAndIdNot(draft.name(), excludeId)) {
            violations.add(ApiErrorCode.DUPLICATE_RESOURCE, "name", "finance.taxRate.name.duplicate");
        }
    }

    private static Changes diff(TaxRateDraft from, TaxRateDraft to) {
        Optional<TaxRateDraft> f = Optional.ofNullable(from);
        Optional<TaxRateDraft> t = Optional.ofNullable(to);
        Changes changes = Changes.start();
        Map.<String, Function<TaxRateDraft, Object>>of(
                        "code", TaxRateDraft::code, "name", TaxRateDraft::name, "description", TaxRateDraft::description,
                        "factorType", TaxRateDraft::factorType, "ratePercent", TaxRateDraft::ratePercent,
                        "validFrom", TaxRateDraft::validFrom, "validTo", TaxRateDraft::validTo)
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> changes.track(e.getKey(), f.map(e.getValue()).orElse(null), t.map(e.getValue()).orElse(null)));
        return changes;
    }

    private TaxRateEntity load(long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("finance.taxRate.notFound"));
    }

    private TaxRateResponse view(long id) {
        return queries.find(id).orElseThrow(() -> ApiException.notFound("finance.taxRate.notFound"));
    }

    private static String label(TaxRateEntity entity) {
        return entity.getName() + " (" + entity.getCode() + ")";
    }

    private UUID actorId() {
        return actors.require().id();
    }

    private Instant now() {
        return TenantTime.now(clock);
    }
}
