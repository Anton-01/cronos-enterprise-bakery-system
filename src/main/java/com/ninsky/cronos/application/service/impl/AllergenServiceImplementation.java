package com.ninsky.cronos.application.service.impl;

import com.ninsky.cronos.application.imports.ImportProperties;
import com.ninsky.cronos.application.imports.csv.CsvCatalogFile;
import com.ninsky.cronos.application.request.core.AllergenRequest;
import com.ninsky.cronos.application.request.status.ChangeStatusRequest;
import com.ninsky.cronos.application.response.core.AllergenResponse;
import com.ninsky.cronos.application.response.imports.core.CsvImportResponse;
import com.ninsky.cronos.application.service.AllergenService;
import com.ninsky.cronos.application.service.audit.CatalogAuditTrail;
import com.ninsky.cronos.domain.model.audit.Actor;
import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.domain.model.core.Allergen;
import com.ninsky.cronos.domain.port.core.AllergenRepositoryPort;
import com.ninsky.cronos.infrastructure.exception.DuplicateResourceException;
import com.ninsky.cronos.infrastructure.exception.ResourceNotFoundException;
import com.ninsky.cronos.infrastructure.exception.SystemResourceException;
import com.ninsky.cronos.domain.port.auth.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service @Slf4j
@RequiredArgsConstructor
public class AllergenServiceImplementation implements AllergenService {

    private static final String COLUMN_NAME = "name";
    private static final String COLUMN_ALTERNATIVE_NAME = "alternativeName";
    private static final String COLUMN_DESCRIPTION = "description";
    private static final int NAME_MAX_LENGTH = 100;
    private static final int DESCRIPTION_MAX_LENGTH = 500;

    private final AllergenRepositoryPort allergenRepository;
    private final UserRepositoryPort userRepository;
    private final CatalogAuditTrail auditTrail;
    private final ImportProperties importProperties;

    /**
     * Creates a new Allergen
     */
    @Transactional
    @Override
    public AllergenResponse createAllergen(AllergenRequest request, String username) {
        if (allergenRepository.existsByName(request.name())) {
            throw new DuplicateResourceException("Allergen already exists");
        }

        Allergen allergen = Allergen.builder().name(request.name().trim())
                .alternativeName(request.alternativeName().trim())
                .description(request.description() == null ? null : request.description().trim())
                .build();

        allergen = allergenRepository.save(allergen);

        log.info("Allergen created: {} ", allergen.getName());

        return mapToResponse(allergen);
    }

    /**
     * Gets all allergens available for a user (system + user's own) - paginated
     */
    @Transactional(readOnly = true)
    @Override
    public Page<AllergenResponse> getUserAllergens(Pageable pageable) {
        log.debug("Fetching paginated Allergens By User. Page: {}, Size: {}", pageable.getPageNumber(), pageable.getPageSize());
        return allergenRepository.findAllByOrderByIdAsc(pageable).map(this::mapToResponse);
    }

    /**
     * Gets system allergens paginated
     */
    @Transactional(readOnly = true)
    @Override
    public Page<AllergenResponse> getSystemAllergens(Pageable pageable) {
        log.debug("Fetching paginated Allergens System. Page: {}, Size: {}", pageable.getPageNumber(), pageable.getPageSize());
        return allergenRepository.findSystemAllergens(pageable).map(this::mapToResponse);
    }

    /**
     * Update a new allergen
     */
    @Transactional
    @Override
    public AllergenResponse updateAllergen(UUID allergenId, AllergenRequest request, String username) {
        userRepository.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado o token inválido"));

        Allergen allergen = allergenRepository.findById(allergenId)
                .orElseThrow(() -> new ResourceNotFoundException("Allergen not found with id: " + allergenId));

        if (Boolean.TRUE.equals(allergen.getIsSystemDefault())) {
            throw new SystemResourceException("System default allergen cannot be edited");
        }

        if (!allergen.getName().equalsIgnoreCase(request.name().trim()) && allergenRepository.existsByName(request.name().trim())) {
            throw new DuplicateResourceException("Allergen name already exists");
        }

        allergen.setName(request.name().trim());
        allergen.setAlternativeName(request.alternativeName().trim());
        allergen.setDescription(request.description() == null ? null : request.description().trim());

        allergen = allergenRepository.save(allergen);
        log.info("Allergen updated: ID {}, Name: {}", allergen.getId(), allergen.getName());

        return mapToResponse(allergen);
    }

    /**
     * Imports system allergens (upsert by name). All-or-nothing: every row is validated first and
     * nothing is written if any row is invalid.
     */
    @Transactional
    @Override
    public CsvImportResponse importAllergensFromCsv(MultipartFile file, Actor actor) {
        CsvCatalogFile csv = CsvCatalogFile.parse(file, Set.of(COLUMN_NAME, COLUMN_ALTERNATIVE_NAME, COLUMN_DESCRIPTION),
                importProperties.maxRows());
        Map<String, Integer> firstLineOfKey = new HashMap<>();
        List<Allergen> rows = new ArrayList<>();

        for (CSVRecord record : csv.records()) {
            Optional<String> name = csv.requiredText(record, COLUMN_NAME, NAME_MAX_LENGTH);
            Optional<String> alternativeName = csv.requiredText(record, COLUMN_ALTERNATIVE_NAME, NAME_MAX_LENGTH);
            Optional<String> description = csv.optionalText(record, COLUMN_DESCRIPTION, DESCRIPTION_MAX_LENGTH);
            if (csv.hasErrors(record) || csv.isDuplicate(record, COLUMN_NAME, name.get(), name.get().toLowerCase(Locale.ROOT), firstLineOfKey)) {
                continue;
            }
            rows.add(Allergen.builder().name(name.get()).alternativeName(alternativeName.get())
                    .description(description.orElse(null)).isSystemDefault(true).build());
        }
        csv.rejectIfInvalid();

        List<String> created = new ArrayList<>();
        List<String> updated = new ArrayList<>();
        for (Allergen row : rows) {
            Optional<Allergen> existing = allergenRepository.findByName(row.getName());
            if (existing.isPresent()) {
                Allergen allergen = existing.get();
                allergen.setAlternativeName(row.getAlternativeName());
                allergen.setDescription(row.getDescription());
                allergenRepository.save(allergen);
                updated.add(row.getName());
            } else {
                allergenRepository.save(row);
                created.add(row.getName());
            }
        }
        String summary = "System allergens CSV '%s': %d rows, %d created, %d updated".formatted(
                file.getOriginalFilename(), rows.size(), created.size(), updated.size());
        auditTrail.record(actor, AuditAction.DATA_IMPORT_COMMITTED, CatalogAuditTrail.TARGET_ALLERGEN, null, null, summary);
        log.info("{} (by {})", summary, actor.username());

        return CsvImportResponse.builder().createdCategories(created).updatedCategories(updated).totalProcessed(rows.size()).build();
    }

    @Transactional
    public void changeStatus(UUID id, ChangeStatusRequest request) {
        log.info("Updating the status of Allergen with ID {} to {}", id, request.status());

        int updatedRows = allergenRepository.updateStatus(id, request.status());

        if (updatedRows == 0) {
            log.warn("Attempt to change the status of a non-existent Allergen: {}", id);
            throw new ResourceNotFoundException("El Alérgeno con ID " + id + " no existe.");
        }
    }

    private AllergenResponse mapToResponse(Allergen allergen) {
        return AllergenResponse.builder().id(allergen.getId())
                .name(allergen.getName()).alternativeName(allergen.getAlternativeName())
                .description(allergen.getDescription()).isSystemDefault(allergen.getIsSystemDefault())
                .status(allergen.getStatus().name()).build();
    }
}
