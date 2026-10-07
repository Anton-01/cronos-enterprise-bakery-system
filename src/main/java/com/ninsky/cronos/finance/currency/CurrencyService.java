package com.ninsky.cronos.finance.currency;

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

/** Currency catalog use cases (spec §9): validation, single default, audit in the same transaction (N4). */
@Service
@RequiredArgsConstructor
public class CurrencyService {

    private static final long NO_ID = -1L;

    private final CurrencyRepository repository;
    private final CurrencyQueryCustomRepository queries;
    private final FinanceLocks locks;
    private final FinanceSettingsCache settingsCache;
    private final AuditRecorder audit;
    private final ActorProvider actors;
    private final Clock clock;

    @Transactional(readOnly = true)
    public CatalogPage<CurrencyResponse> page(String search, FinanceStatus status, Integer page, Integer size, String sort) {
        return queries.page(search, status, PageQuery.of(page, size, sort, CurrencyQueryCustomRepository.SORTS, CurrencyQueryCustomRepository.DEFAULT_SORT));
    }

    @Transactional(readOnly = true)
    public List<CurrencyOption> catalog() {
        return repository.findByStatusOrderByIsDefaultDescCodeAsc(FinanceStatus.ACTIVE).stream().map(CurrencyOption::of).toList();
    }

    @Transactional
    public CurrencyResponse create(CurrencyRequest request) {
        CurrencyDraft draft = CurrencyDraft.from(request);
        Violations violations = new Violations();
        CurrencyRules.check(draft.code(), draft.numericCode()).forEach(i -> violations.invalid(i.field(), i.messageKey(), i.args().toArray()));
        checkUniqueness(draft, NO_ID, violations);
        violations.throwIfAny();

        CurrencyEntity saved = repository.saveAndFlush(CurrencyEntity.create(draft, actorId(), now()));
        audit.record(AuditEvent.of(AuditAction.CURRENCY_CREATED, AuditTargets.CURRENCY, saved.getId(), label(saved))
                .changes(diff(null, draft).build())
                .build());
        return view(saved.getId());
    }

    @Transactional
    public CurrencyResponse update(long id, CurrencyRequest request) {
        if (request.version() == null) {
            throw ApiException.invalid("version", "api.validation.required");
        }
        CurrencyEntity entity = load(id);
        FinanceLocks.requireVersion(request.version(), entity.getVersion());
        CurrencyDraft current = entity.draft();
        CurrencyDraft draft = CurrencyDraft.from(request);

        Violations violations = new Violations();
        CurrencyRules.check(draft.code(), draft.numericCode()).forEach(i -> violations.invalid(i.field(), i.messageKey(), i.args().toArray()));
        checkUniqueness(draft, id, violations);
        List<String> frozen = draft.immutableFieldsChangedFrom(current);
        if (!frozen.isEmpty() && queries.inUse(id)) {
            frozen.forEach(field -> violations.add(ApiErrorCode.RESOURCE_IN_USE, field, "finance.currency.immutableInUse"));
        }
        violations.throwIfAny();

        Changes changes = diff(current, draft);
        if (changes.isEmpty()) {
            return view(id);
        }
        entity.update(draft, actorId(), now());
        repository.saveAndFlush(entity);
        audit.record(AuditEvent.of(AuditAction.CURRENCY_UPDATED, AuditTargets.CURRENCY, id, label(entity)).changes(changes.build()).build());
        if (entity.isDefault()) {
            settingsCache.evict();
        }
        return view(id);
    }

    @Transactional
    public CurrencyResponse changeStatus(long id, StatusRequest request) {
        CurrencyEntity entity = load(id);
        FinanceLocks.requireVersion(request.version(), entity.getVersion());
        if (entity.getStatus() == request.status()) {
            return view(id);
        }
        if (entity.isDefault()) {
            throw ApiException.of(ApiErrorCode.DEFAULT_LOCKED, "status", "finance.currency.defaultLocked.deactivate");
        }
        FinanceStatus previous = entity.getStatus();
        entity.changeStatus(request.status(), actorId(), now());
        repository.saveAndFlush(entity);
        audit.record(AuditEvent.of(AuditAction.CURRENCY_STATUS_CHANGED, AuditTargets.CURRENCY, id, label(entity))
                .changes(Changes.start().track("status", previous, request.status()).build())
                .params(Map.of("detail", request.status().name()))
                .build());
        return view(id);
    }

    @Transactional
    public CurrencyResponse makeDefault(long id, VersionRequest request) {
        locks.lockDefaults();
        CurrencyEntity target = load(id);
        FinanceLocks.requireVersion(request.version(), target.getVersion());
        if (target.isDefault()) {
            return view(id);
        }
        if (!target.isActive()) {
            throw ApiException.of(ApiErrorCode.INVALID_STATE_TRANSITION, null, "finance.currency.default.inactive");
        }
        UUID actor = actorId();
        Instant now = now();
        Optional<CurrencyEntity> previous = repository.findByIsDefaultTrue();
        previous.ifPresent(old -> {
            old.markDefault(false, actor, now);
            repository.saveAndFlush(old);
        });
        target.markDefault(true, actor, now);
        repository.saveAndFlush(target);
        audit.record(AuditEvent.of(AuditAction.FINANCE_DEFAULT_CHANGED, AuditTargets.CURRENCY, id, label(target))
                .severity(AuditSeverity.WARNING)
                .changes(Changes.start().track("defaultCurrency", previous.map(CurrencyEntity::getCode).orElse(null), target.getCode()).build())
                .params(Map.of("detail", target.getCode()))
                .build());
        settingsCache.evict();
        return view(id);
    }

    @Transactional
    public void delete(long id) {
        CurrencyEntity entity = load(id);
        if (entity.isDefault()) {
            throw ApiException.of(ApiErrorCode.DEFAULT_LOCKED, null, "finance.currency.defaultLocked.delete");
        }
        if (queries.inUse(id)) {
            throw ApiException.of(ApiErrorCode.RESOURCE_IN_USE, null, "finance.currency.inUse.delete");
        }
        repository.delete(entity);
        repository.flush();
        audit.record(AuditEvent.of(AuditAction.CURRENCY_DELETED, AuditTargets.CURRENCY, id, label(entity))
                .changes(diff(entity.draft(), null).build())
                .build());
    }

    private void checkUniqueness(CurrencyDraft draft, long excludeId, Violations violations) {
        if (draft.code() != null && repository.existsByCodeAndIdNot(draft.code(), excludeId)) {
            violations.add(ApiErrorCode.DUPLICATE_RESOURCE, "code", "finance.currency.code.duplicate");
        }
        if (draft.numericCode() != null && repository.existsByNumericCodeAndIdNot(draft.numericCode(), excludeId)) {
            violations.add(ApiErrorCode.DUPLICATE_RESOURCE, "numericCode", "finance.currency.numericCode.duplicate");
        }
        if (draft.name() != null && repository.existsByNameIgnoreCaseAndIdNot(draft.name(), excludeId)) {
            violations.add(ApiErrorCode.DUPLICATE_RESOURCE, "name", "finance.currency.name.duplicate");
        }
    }

    private static Changes diff(CurrencyDraft from, CurrencyDraft to) {
        Optional<CurrencyDraft> f = Optional.ofNullable(from);
        Optional<CurrencyDraft> t = Optional.ofNullable(to);
        return Changes.start()
                .track("code", f.map(CurrencyDraft::code).orElse(null), t.map(CurrencyDraft::code).orElse(null))
                .track("numericCode", f.map(CurrencyDraft::numericCode).orElse(null), t.map(CurrencyDraft::numericCode).orElse(null))
                .track("name", f.map(CurrencyDraft::name).orElse(null), t.map(CurrencyDraft::name).orElse(null))
                .track("symbol", f.map(CurrencyDraft::symbol).orElse(null), t.map(CurrencyDraft::symbol).orElse(null))
                .track("decimalPlaces", f.map(CurrencyDraft::decimalPlaces).orElse(null), t.map(CurrencyDraft::decimalPlaces).orElse(null))
                .track("symbolPosition", f.map(CurrencyDraft::symbolPosition).orElse(null), t.map(CurrencyDraft::symbolPosition).orElse(null));
    }

    private CurrencyEntity load(long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("finance.currency.notFound"));
    }

    private CurrencyResponse view(long id) {
        return queries.find(id).orElseThrow(() -> ApiException.notFound("finance.currency.notFound"));
    }

    private static String label(CurrencyEntity entity) {
        return entity.getName() + " (" + entity.getCode() + ")";
    }

    private UUID actorId() {
        return actors.require().id();
    }

    private Instant now() {
        return TenantTime.now(clock);
    }
}
