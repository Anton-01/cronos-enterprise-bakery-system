package com.ninsky.cronos.application.service.impl;

import com.ninsky.cronos.application.request.core.CreateCategoryRequest;
import com.ninsky.cronos.application.request.core.UpdateCategoryRequest;
import com.ninsky.cronos.application.response.core.CategoryResponse;
import com.ninsky.cronos.application.response.imports.core.CsvImportResponse;
import com.ninsky.cronos.application.service.CategoryService;
import com.ninsky.cronos.domain.entity.enums.CategoryScope;
import com.ninsky.cronos.domain.entity.enums.CategoryType;
import com.ninsky.cronos.domain.model.core.Category;
import com.ninsky.cronos.domain.port.core.CategoryRepositoryPort;
import com.ninsky.cronos.infrastructure.exception.DuplicateResourceException;
import com.ninsky.cronos.infrastructure.exception.ResourceNotFoundException;
import com.ninsky.cronos.infrastructure.exception.SystemResourceException;
import com.ninsky.cronos.infrastructure.exception.UnauthorizedCategoryModificationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service @Slf4j
@RequiredArgsConstructor
public class CategoryServiceImplementation implements CategoryService {

    private final CategoryRepositoryPort categoryRepository;

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
     *  a use case (a baker adds their own categories one at a time via {@link #createCategory}). */
    @Transactional
    @Override
    public CsvImportResponse importCategoriesFromCsv(MultipartFile file) {
        List<String> created = new ArrayList<>();
        List<String> updated = new ArrayList<>();
        int total = 0;

        CSVFormat csvFormat = CSVFormat.Builder.create().setHeader().setSkipHeaderRecord(true).setIgnoreSurroundingSpaces(true).get();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)); CSVParser csvParser = csvFormat.parse(reader)) {

            for (CSVRecord record : csvParser) {
                String name = record.get("name").trim();
                String description = record.get("description").trim();
                CategoryType type = CategoryType.valueOf(record.get("type").trim().toUpperCase());

                Optional<Category> existingCategory = categoryRepository.findByNameForOwner(name, type, null);

                if (existingCategory.isPresent()) {
                    Category category = existingCategory.get().toBuilder().description(description).build();
                    categoryRepository.save(category);
                    updated.add(name);
                } else {
                    Category newCategory = Category.builder()
                            .name(name).description(description).type(type).scope(CategoryScope.SYSTEM).build();
                    categoryRepository.save(newCategory);
                    created.add(name);
                }
                total++;
            }
            log.info("CSV Processed: {} categories ({} created, {} updated)", total, created.size(), updated.size());

        } catch (Exception e) {
            log.error("Error processing Category CSV file", e);
            throw new RuntimeException("Error processing CSV file: " + e.getMessage());
        }

        return CsvImportResponse.builder().createdCategories(created).updatedCategories(updated).totalProcessed(total).build();
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
