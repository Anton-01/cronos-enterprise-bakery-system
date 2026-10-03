package com.ninsky.cronos.application.service.impl;

import com.ninsky.cronos.application.imports.ImportProperties;
import com.ninsky.cronos.application.imports.csv.CsvCatalogFile;
import com.ninsky.cronos.application.request.core.CreateCategoryRequest;
import com.ninsky.cronos.application.request.core.UpdateCategoryRequest;
import com.ninsky.cronos.application.response.core.CategoryResponse;
import com.ninsky.cronos.application.response.imports.core.CsvImportResponse;
import com.ninsky.cronos.application.service.CategoryService;
import com.ninsky.cronos.application.service.audit.CatalogAuditTrail;
import com.ninsky.cronos.domain.entity.enums.CategoryScope;
import com.ninsky.cronos.domain.entity.enums.CategoryType;
import com.ninsky.cronos.domain.model.audit.Actor;
import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.domain.model.core.Category;
import com.ninsky.cronos.domain.port.core.CategoryRepositoryPort;
import com.ninsky.cronos.infrastructure.exception.DuplicateResourceException;
import com.ninsky.cronos.infrastructure.exception.ResourceNotFoundException;
import com.ninsky.cronos.infrastructure.exception.SystemResourceException;
import com.ninsky.cronos.infrastructure.exception.UnauthorizedCategoryModificationException;
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
public class CategoryServiceImplementation implements CategoryService {

    private static final String COLUMN_NAME = "name";
    private static final String COLUMN_DESCRIPTION = "description";
    private static final String COLUMN_TYPE = "type";
    private static final int NAME_MAX_LENGTH = 100;
    private static final int DESCRIPTION_MAX_LENGTH = 500;

    private final CategoryRepositoryPort categoryRepository;
    private final CatalogAuditTrail auditTrail;
    private final ImportProperties importProperties;

    /** Every category created through this endpoint is USER-scoped and owned by the caller —
     *  SYSTEM categories are seed-only (DataSeeder/CategoryDataSeeder) or CSV-imported, never
     *  created through the normal REST path. */
    @Transactional
    @Override
    public CategoryResponse createCategory(CreateCategoryRequest request, UUID currentUserId) {
        String name = request.name().trim();

        if (categoryRepository.existsByNameForOwner(name, request.type(), currentUserId)) {
            throw new DuplicateResourceException("You already have a category named '" + name + "'");
        }

        Category category = Category.builder()
                .name(name)
                .description(request.description().trim())
                .type(request.type())
                .scope(CategoryScope.USER)
                .userId(currentUserId)
                .build();

        category = categoryRepository.save(category);
        log.info("Category created: {} (type={}, owner={})", category.name(), category.type(), currentUserId);

        return mapToResponse(category);
    }

    @Transactional
    @Override
    public CategoryResponse updateCategory(Long id, UpdateCategoryRequest request, UUID currentUserId) {
        Category category = requireOwnedByCaller(id, currentUserId, "edit");

        String newName = request.name().trim();
        if (!category.name().equalsIgnoreCase(newName)
                && categoryRepository.existsByNameForOwner(newName, category.type(), currentUserId)) {
            throw new DuplicateResourceException("You already have a category named '" + newName + "'");
        }

        Category updated = category.toBuilder()
                .name(newName)
                .description(request.description().trim())
                .build();

        updated = categoryRepository.save(updated);
        log.info("Category updated: ID {}, Name: {}", updated.id(), updated.name());

        return mapToResponse(updated);
    }

    @Transactional
    @Override
    public void trashCategory(Long id, UUID currentUserId) {
        Category category = requireOwnedByCaller(id, currentUserId, "delete");
        categoryRepository.delete(category);
        log.info("Category trashed: ID {}, owner {}", id, currentUserId);
    }

    /**
     * Loads the category and enforces business rules 2+3 in one place: a SYSTEM row is never
     * mutable through this service ({@link SystemResourceException}, regardless of who's asking);
     * a USER row not owned by {@code currentUserId} is rejected as an IDOR attempt
     * ({@link UnauthorizedCategoryModificationException}) rather than silently 404ing — the caller
     * already knows the id exists (they're targeting it directly), so there's nothing to hide by
     * pretending otherwise, unlike the list endpoints (which filter it out of results entirely).
     */
    private Category requireOwnedByCaller(Long id, UUID currentUserId, String action) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + id));

        if (category.isSystem()) {
            throw new SystemResourceException("System category cannot be " + action + "d");
        }
        if (!category.isOwnedBy(currentUserId)) {
            throw new UnauthorizedCategoryModificationException(
                    "You do not have permission to " + action + " this category");
        }
        return category;
    }

    @Transactional(readOnly = true)
    @Override
    public Page<CategoryResponse> getVisibleCategories(CategoryType type, Pageable pageable, UUID currentUserId) {
        log.debug("Fetching visible categories for user {}. type={}, page={}", currentUserId, type, pageable);
        return categoryRepository.findVisibleToUser(currentUserId, type, pageable).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<CategoryResponse> getSystemCategories(CategoryType type, Pageable pageable) {
        log.debug("Fetching system categories. type={}, page={}", type, pageable);
        return categoryRepository.findSystemCategories(type, pageable).map(this::mapToResponse);
    }

    /** CSV import only ever creates/updates SYSTEM categories — bulk USER-category creation isn't
     *  a use case (a baker adds their own categories one at a time via {@link #createCategory}).
     *  All-or-nothing: every row is validated first and nothing is written if any row is invalid. */
    @Transactional
    @Override
    public CsvImportResponse importCategoriesFromCsv(MultipartFile file, Actor actor) {
        CsvCatalogFile csv = CsvCatalogFile.parse(file, Set.of(COLUMN_NAME, COLUMN_DESCRIPTION, COLUMN_TYPE), importProperties.maxRows());
        Map<String, Integer> firstLineOfKey = new HashMap<>();
        List<Category> rows = new ArrayList<>();

        for (CSVRecord record : csv.records()) {
            Optional<String> name = csv.requiredText(record, COLUMN_NAME, NAME_MAX_LENGTH);
            Optional<String> description = csv.requiredText(record, COLUMN_DESCRIPTION, DESCRIPTION_MAX_LENGTH);
            Optional<CategoryType> type = csv.requiredEnum(record, COLUMN_TYPE, CategoryType.class);
            if (csv.hasErrors(record)
                    || csv.isDuplicate(record, COLUMN_NAME, name.get(), type.get() + ":" + name.get().toLowerCase(Locale.ROOT), firstLineOfKey)) {
                continue;
            }
            rows.add(Category.builder().name(name.get()).description(description.get()).type(type.get()).scope(CategoryScope.SYSTEM).build());
        }
        csv.rejectIfInvalid();

        List<String> created = new ArrayList<>();
        List<String> updated = new ArrayList<>();
        for (Category row : rows) {
            Optional<Category> existing = categoryRepository.findByNameForOwner(row.name(), row.type(), null);
            if (existing.isPresent()) {
                categoryRepository.save(existing.get().toBuilder().description(row.description()).build());
                updated.add(row.name());
            } else {
                categoryRepository.save(row);
                created.add(row.name());
            }
        }
        String summary = "SYSTEM categories CSV '%s': %d rows, %d created, %d updated".formatted(
                file.getOriginalFilename(), rows.size(), created.size(), updated.size());
        auditTrail.record(actor, AuditAction.DATA_IMPORT_COMMITTED, CatalogAuditTrail.TARGET_CATEGORY, null, null, summary);
        log.info("{} (by {})", summary, actor.username());

        return CsvImportResponse.builder().createdCategories(created).updatedCategories(updated).totalProcessed(rows.size()).build();
    }

    private CategoryResponse mapToResponse(Category category) {
        return CategoryResponse.builder()
                .id(category.id())
                .name(category.name())
                .description(category.description())
                .type(category.type().name())
                .scope(category.scope().name())
                .status(category.status().name())
                .userId(category.userId())
                .build();
    }
}
