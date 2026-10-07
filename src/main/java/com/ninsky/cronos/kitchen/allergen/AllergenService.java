package com.ninsky.cronos.kitchen.allergen;

import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.finance.shared.Changes;
import com.ninsky.cronos.iam.audit.AuditEvent;
import com.ninsky.cronos.iam.audit.AuditRecorder;
import com.ninsky.cronos.iam.audit.AuditTargets;
import com.ninsky.cronos.iam.shared.ActorProvider;
import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.infrastructure.exception.Violations;
import com.ninsky.cronos.kitchen.shared.KitchenMessages;
import com.ninsky.cronos.kitchen.shared.KitchenStatus;
import com.ninsky.cronos.kitchen.shared.Scope;
import com.ninsky.cronos.kitchen.shared.StatusRequest;
import com.ninsky.cronos.kitchen.shared.TextNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Allergen use cases (§3): SYSTEM ∪ own, tenant keywords on SYSTEM rows, detection. */
@Service
@RequiredArgsConstructor
public class AllergenService {

    private static final List<String> LOCALES = List.of("es", "en");

    private final AllergenCatalog catalog;
    private final AllergenCustomRepository repository;
    private final AuditRecorder audit;
    private final ActorProvider actors;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<AllergenResponse> list(KitchenStatus status) {
        UUID tenant = tenant();
        String language = KitchenMessages.language();
        Map<Long, Long> counts = repository.ingredientCounts(tenant);
        return catalog.view(tenant).byId().values().stream()
                .filter(e -> status == null || e.status() == status)
                .map(e -> AllergenResponse.of(e, language, counts.getOrDefault(e.id(), 0L)))
                .sorted(java.util.Comparator.comparing(AllergenResponse::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional(readOnly = true)
    public AllergenResponse get(long id) {
        return view(id);
    }

    @Transactional
    public AllergenResponse create(AllergenRequest request) {
        UUID tenant = tenant();
        AllergenCatalog.View visible = catalog.view(tenant);
        Violations violations = new Violations();
        AllergenRules.check(request, true, violations);
        checkUniqueness(visible, request, null, violations);
        violations.throwIfAny();

        String name = request.name().strip();
        List<String> keywords = AllergenRules.keywords(request.keywords());
        long id = repository.insert(request.code(), tenant, request.icon(), List.copyOf(new LinkedHashSet<>(request.regulations())), tenant, now());
        repository.upsertText(id, LOCALES, name, blankToNull(request.description()));
        repository.replaceKeywords(id, tenant, KitchenMessages.language(), keywords);
        catalog.evict(tenant);
        audit.record(AuditEvent.of(AuditAction.ALLERGEN_CREATED, AuditTargets.ALLERGEN, id, name)
                .changes(Changes.start().track("code", null, request.code()).track("name", null, name)
                        .track("keywords", null, keywords).build())
                .build());
        return view(id);
    }

    @Transactional
    public AllergenResponse update(long id, AllergenRequest request) {
        if (request.version() == null) {
            throw ApiException.invalid("version", "api.validation.required");
        }
        UUID tenant = tenant();
        AllergenCatalog.Entry current = entry(id);
        String language = KitchenMessages.language();
        return current.scope() == Scope.SYSTEM
                ? updateSystemKeywords(current, request, tenant, language)
                : updateOwn(current, request, tenant, language);
    }

    private AllergenResponse updateOwn(AllergenCatalog.Entry current, AllergenRequest request, UUID tenant, String language) {
        Violations violations = new Violations();
        AllergenRules.check(request, false, violations);
        violations.invalidIf(request.code() != null && !request.code().equals(current.code()), "code", "kitchen.code.immutable");
        checkUniqueness(catalog.view(tenant), request, current.id(), violations);
        violations.throwIfAny();

        String name = request.name().strip();
        List<String> keywords = AllergenRules.keywords(request.keywords());
        List<String> regulations = List.copyOf(new LinkedHashSet<>(request.regulations()));
        Changes changes = Changes.start()
                .track("name", current.name(language), name)
                .track("description", current.description(language), blankToNull(request.description()))
                .track("icon", current.icon(), request.icon())
                .track("regulations", current.regulations(), regulations)
                .track("keywords", current.keywords(language), keywords);
        if (changes.isEmpty()) {
            requireVersion(request.version(), current.version());
            return view(current.id());
        }
        if (!repository.update(current.id(), request.version(), request.icon(), regulations, tenant, now())) {
            throw ApiException.concurrentModification();
        }
        repository.upsertText(current.id(), LOCALES, name, blankToNull(request.description()));
        repository.replaceKeywords(current.id(), tenant, language, keywords);
        catalog.evict(tenant);
        audit.record(AuditEvent.of(AuditAction.ALLERGEN_UPDATED, AuditTargets.ALLERGEN, current.id(), name).changes(changes.build()).build());
        return view(current.id());
    }

    /** SYSTEM rows: only the tenant's own keywords change; any other changed field is a conflict. */
    private AllergenResponse updateSystemKeywords(AllergenCatalog.Entry current, AllergenRequest request, UUID tenant, String language) {
        Violations violations = new Violations();
        AllergenRules.checkKeywords(request.keywords(), violations);
        violations.throwIfAny();
        conflictIfChanged(violations, "code", request.code() != null && !request.code().equals(current.code()));
        conflictIfChanged(violations, "name", request.name() != null && !TextNormalizer.fold(request.name()).equals(TextNormalizer.fold(current.name(language))));
        conflictIfChanged(violations, "description", request.description() != null
                && !Objects.equals(blankToNull(request.description()), current.description(language)));
        conflictIfChanged(violations, "icon", request.icon() != null && !request.icon().equals(current.icon()));
        conflictIfChanged(violations, "regulations", !request.regulations().isEmpty()
                && !Set.copyOf(request.regulations()).equals(Set.copyOf(current.regulations())));
        violations.throwIfAny();

        Set<String> platform = current.allPlatformKeywords();
        List<String> own = AllergenRules.keywords(request.keywords()).stream().filter(k -> !platform.contains(k)).toList();
        Set<String> previous = current.allTenantKeywords();
        if (previous.equals(Set.copyOf(own))) {
            requireVersion(request.version(), current.version());
            return view(current.id());
        }
        if (!repository.touch(current.id(), request.version(), tenant, now())) {
            throw ApiException.concurrentModification();
        }
        repository.replaceKeywords(current.id(), tenant, language, own);
        catalog.evictAll();
        audit.record(AuditEvent.of(AuditAction.ALLERGEN_UPDATED, AuditTargets.ALLERGEN, current.id(), current.name(language))
                .changes(Changes.start().track("tenantKeywords", List.copyOf(previous), own).build())
                .build());
        return view(current.id());
    }

    @Transactional
    public AllergenResponse changeStatus(long id, StatusRequest request) {
        UUID tenant = tenant();
        AllergenCatalog.Entry current = entry(id);
        if (current.scope() == Scope.SYSTEM) {
            throw ApiException.of(ApiErrorCode.SYSTEM_RESOURCE_CONFLICT, "status", "kitchen.system.readOnly");
        }
        requireVersion(request.version(), current.version());
        if (current.status() == request.status()) {
            return view(id);
        }
        if (!repository.changeStatus(id, request.version(), request.status(), tenant, now())) {
            throw ApiException.concurrentModification();
        }
        catalog.evict(tenant);
        audit.record(AuditEvent.of(AuditAction.ALLERGEN_STATUS_CHANGED, AuditTargets.ALLERGEN, id, current.name(KitchenMessages.language()))
                .changes(Changes.start().track("status", current.status(), request.status()).build())
                .params(Map.of("detail", request.status().name()))
                .build());
        return view(id);
    }

    @Transactional
    public void delete(long id) {
        UUID tenant = tenant();
        AllergenCatalog.Entry current = entry(id);
        if (current.scope() == Scope.SYSTEM) {
            throw ApiException.of(ApiErrorCode.SYSTEM_RESOURCE_CONFLICT, null, "kitchen.system.readOnly");
        }
        if (repository.inUse(id)) {
            throw ApiException.of(ApiErrorCode.RESOURCE_IN_USE, null, "kitchen.allergen.inUse");
        }
        repository.delete(id);
        catalog.evict(tenant);
        audit.record(AuditEvent.of(AuditAction.ALLERGEN_DELETED, AuditTargets.ALLERGEN, id, current.name(KitchenMessages.language()))
                .changes(Changes.start().track("code", current.code(), null).build())
                .build());
    }

    /** §3.3: suggestions only, names in the caller's locale. */
    @Transactional(readOnly = true)
    public List<DetectedAllergen> detect(DetectRequest request) {
        AllergenCatalog.View visible = catalog.view(tenant());
        String language = KitchenMessages.language();
        return visible.detector().detect(request.text(), request.excludeIds()).stream()
                .map(m -> new DetectedAllergen(m.allergenId(), m.code(),
                        visible.find(m.allergenId()).map(e -> e.name(language)).orElse(m.name()), m.keyword()))
                .toList();
    }

    private void checkUniqueness(AllergenCatalog.View visible, AllergenRequest request, Long selfId, Violations violations) {
        String folded = TextNormalizer.fold(request.name());
        visible.byId().values().stream().filter(e -> !Objects.equals(e.id(), selfId)).forEach(e -> {
            if (selfId == null && e.code().equals(request.code()) && !violations.hasField("code")) {
                violations.add(ApiErrorCode.DUPLICATE_RESOURCE, "code", "kitchen.code.duplicate");
            }
            if (!folded.isEmpty() && e.names().values().stream().map(TextNormalizer::fold).anyMatch(folded::equals)
                    && !violations.hasField("name")) {
                violations.add(ApiErrorCode.DUPLICATE_RESOURCE, "name", "kitchen.name.duplicate");
            }
        });
    }

    private static void conflictIfChanged(Violations violations, String field, boolean changed) {
        if (changed) {
            violations.add(ApiErrorCode.SYSTEM_RESOURCE_CONFLICT, field, "kitchen.allergen.system.keywordsOnly");
        }
    }

    private AllergenCatalog.Entry entry(long id) {
        return catalog.view(tenant()).find(id).orElseThrow(() -> ApiException.notFound("kitchen.allergen.notFound"));
    }

    private AllergenResponse view(long id) {
        UUID tenant = tenant();
        long count = repository.ingredientCounts(tenant).getOrDefault(id, 0L);
        return AllergenResponse.of(entry(id), KitchenMessages.language(), count);
    }

    private static void requireVersion(Long requested, long current) {
        if (requested == null || requested != current) {
            throw ApiException.concurrentModification();
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private UUID tenant() {
        return actors.require().id();
    }

    private Instant now() {
        return clock.instant();
    }
}
