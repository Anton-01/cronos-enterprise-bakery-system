package com.ninsky.cronos.kitchen.legacy;

import com.ninsky.cronos.application.imports.ImportProperties;
import com.ninsky.cronos.application.imports.csv.CsvCatalogFile;
import com.ninsky.cronos.application.request.core.AllergenRequest;
import com.ninsky.cronos.application.request.status.ChangeStatusRequest;
import com.ninsky.cronos.application.response.core.AllergenResponse;
import com.ninsky.cronos.application.response.imports.core.CsvImportResponse;
import com.ninsky.cronos.application.service.AllergenService;
import com.ninsky.cronos.application.service.audit.CatalogAuditTrail;
import com.ninsky.cronos.domain.entity.enums.RecordStatus;
import com.ninsky.cronos.domain.model.audit.Actor;
import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.kitchen.allergen.AllergenCatalog;
import com.ninsky.cronos.kitchen.allergen.AllergenCustomRepository;
import com.ninsky.cronos.kitchen.shared.KitchenCodes;
import com.ninsky.cronos.kitchen.shared.KitchenStatus;
import com.ninsky.cronos.kitchen.shared.Scope;
import com.ninsky.cronos.kitchen.shared.StatusRequest;
import com.ninsky.cronos.kitchen.shared.TextNormalizer;
import com.ninsky.cronos.iam.shared.ActorProvider;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Deprecated {@code /allergen/**} (§13) on top of the kitchen allergens. Legacy ids are the
 * {@code allergens.legacy_id} UUIDs; {@code name} is Spanish and {@code alternativeName} English.
 */
@Service
@RequiredArgsConstructor
public class LegacyAllergenService implements AllergenService {

    private static final String COLUMN_NAME = "name";
    private static final String COLUMN_ALTERNATIVE_NAME = "alternativeName";
    private static final String COLUMN_DESCRIPTION = "description";
    private static final int NAME_MAX_LENGTH = 100;
    private static final int DESCRIPTION_MAX_LENGTH = 500;
    private static final int CODE_MAX_LENGTH = 50;

    private final com.ninsky.cronos.kitchen.allergen.AllergenService allergens;
    private final AllergenCatalog catalog;
    private final AllergenCustomRepository repository;
    private final ActorProvider actors;
    private final CatalogAuditTrail auditTrail;
    private final ImportProperties importProperties;
    private final Clock clock;

    @Override
    @Transactional
    public AllergenResponse createAllergen(AllergenRequest request, String username) {
        UUID tenant = actors.require().id();
        Set<String> codes = view(tenant).byId().values().stream().map(AllergenCatalog.Entry::code).collect(Collectors.toSet());
        String code = KitchenCodes.unique(KitchenCodes.of(request.name(), "ALG", CODE_MAX_LENGTH), CODE_MAX_LENGTH, codes::contains);
        long id = allergens.create(new com.ninsky.cronos.kitchen.allergen.AllergenRequest(code, request.name(), request.description(),
                null, List.of(request.alternativeName()), List.of(), null)).id();
        return response(entry(tenant, id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AllergenResponse> getUserAllergens(Pageable pageable) {
        return LegacyPages.of(entries(e -> true), pageable, this::response);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AllergenResponse> getSystemAllergens(Pageable pageable) {
        return LegacyPages.of(entries(e -> e.scope() == Scope.SYSTEM), pageable, this::response);
    }

    @Override
    @Transactional
    public AllergenResponse updateAllergen(UUID allergenId, AllergenRequest request, String username) {
        UUID tenant = actors.require().id();
        AllergenCatalog.Entry current = byLegacyId(tenant, allergenId);
        allergens.update(current.id(), new com.ninsky.cronos.kitchen.allergen.AllergenRequest(current.code(), request.name(),
                request.description(), current.icon(), List.of(request.alternativeName()), current.regulations(), current.version()));
        return response(entry(tenant, current.id()));
    }

    @Override
    @Transactional
    public void changeStatus(UUID id, ChangeStatusRequest request) {
        UUID tenant = actors.require().id();
        AllergenCatalog.Entry current = byLegacyId(tenant, id);
        KitchenStatus status = request.status() == RecordStatus.ACTIVE ? KitchenStatus.ACTIVE : KitchenStatus.INACTIVE;
        allergens.changeStatus(current.id(), new StatusRequest(status, current.version()));
    }

    /** Upserts SYSTEM allergens by Spanish name; all-or-nothing as before. */
    @Override
    @Transactional
    public CsvImportResponse importAllergensFromCsv(MultipartFile file, Actor actor) {
        CsvCatalogFile csv = CsvCatalogFile.parse(file, Set.of(COLUMN_NAME, COLUMN_ALTERNATIVE_NAME, COLUMN_DESCRIPTION),
                importProperties.maxRows());
        record Row(String name, String alternativeName, String description) {
        }
        Map<String, Integer> firstLineOfKey = new HashMap<>();
        List<Row> rows = new ArrayList<>();
        for (CSVRecord record : csv.records()) {
            Optional<String> name = csv.requiredText(record, COLUMN_NAME, NAME_MAX_LENGTH);
            Optional<String> alternativeName = csv.requiredText(record, COLUMN_ALTERNATIVE_NAME, NAME_MAX_LENGTH);
            Optional<String> description = csv.optionalText(record, COLUMN_DESCRIPTION, DESCRIPTION_MAX_LENGTH);
            if (csv.hasErrors(record) || csv.isDuplicate(record, COLUMN_NAME, name.get(), TextNormalizer.fold(name.get()), firstLineOfKey)) {
                continue;
            }
            rows.add(new Row(name.get(), alternativeName.get(), description.orElse(null)));
        }
        csv.rejectIfInvalid();

        UUID by = actor.userId();
        Instant now = clock.instant();
        Map<String, AllergenCatalog.Entry> system = view(by).byId().values().stream().filter(e -> e.scope() == Scope.SYSTEM)
                .collect(Collectors.toMap(e -> TextNormalizer.fold(e.name("es")), Function.identity(), (a, b) -> a));
        Set<String> codes = view(by).byId().values().stream().map(AllergenCatalog.Entry::code).collect(Collectors.toCollection(java.util.HashSet::new));
        List<String> created = new ArrayList<>();
        List<String> updated = new ArrayList<>();
        for (Row row : rows) {
            AllergenCatalog.Entry existing = system.get(TextNormalizer.fold(row.name()));
            long id;
            if (existing != null) {
                id = existing.id();
                if (!repository.update(id, existing.version(), existing.icon(), existing.regulations(), by, now)) {
                    throw ApiException.concurrentModification();
                }
                updated.add(row.name());
            } else {
                String code = KitchenCodes.unique(KitchenCodes.of(row.alternativeName(), "ALG", CODE_MAX_LENGTH), CODE_MAX_LENGTH, codes::contains);
                codes.add(code);
                id = repository.insert(code, null, null, List.of(), by, now);
                created.add(row.name());
            }
            repository.upsertText(id, List.of("es"), row.name(), row.description());
            repository.upsertText(id, List.of("en"), row.alternativeName(), row.description());
        }
        catalog.evictAll();
        String summary = "System allergens CSV '%s': %d rows, %d created, %d updated".formatted(
                file.getOriginalFilename(), rows.size(), created.size(), updated.size());
        auditTrail.record(actor, AuditAction.DATA_IMPORT_COMMITTED, CatalogAuditTrail.TARGET_ALLERGEN, null, null, summary);
        return CsvImportResponse.builder().createdCategories(created).updatedCategories(updated).totalProcessed(rows.size()).build();
    }

    private AllergenCatalog.View view(UUID tenant) {
        return catalog.view(tenant);
    }

    private List<AllergenCatalog.Entry> entries(Predicate<AllergenCatalog.Entry> filter) {
        return view(actors.require().id()).byId().values().stream().filter(filter).toList();
    }

    private AllergenCatalog.Entry byLegacyId(UUID tenant, UUID legacyId) {
        return view(tenant).findByLegacyId(legacyId).orElseThrow(() -> ApiException.notFound("kitchen.allergen.notFound"));
    }

    private AllergenCatalog.Entry entry(UUID tenant, long id) {
        return view(tenant).find(id).orElseThrow(() -> ApiException.notFound("kitchen.allergen.notFound"));
    }

    private AllergenResponse response(AllergenCatalog.Entry entry) {
        return AllergenResponse.builder().id(entry.legacyId()).name(entry.name("es"))
                .alternativeName(Optional.ofNullable(entry.names().get("en")).orElse(entry.name("es")))
                .description(entry.description("es")).isSystemDefault(entry.scope() == Scope.SYSTEM)
                .status(entry.status().name()).build();
    }
}
