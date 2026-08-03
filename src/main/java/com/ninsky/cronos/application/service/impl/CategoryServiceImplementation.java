package com.ninsky.cronos.application.service.impl;

import com.ninsky.cronos.application.request.core.CreateCategoryRequest;
import com.ninsky.cronos.application.request.core.UpdateCategoryRequest;
import com.ninsky.cronos.application.request.status.ChangeStatusRequest;
import com.ninsky.cronos.application.response.core.CategoryResponse;
import com.ninsky.cronos.application.response.imports.core.CsvImportResponse;
import com.ninsky.cronos.application.service.CategoryService;
import com.ninsky.cronos.domain.entity.core.Category;
import com.ninsky.cronos.infrastructure.exception.DuplicateResourceException;
import com.ninsky.cronos.infrastructure.exception.ResourceNotFoundException;
import com.ninsky.cronos.infrastructure.exception.SystemResourceException;
import com.ninsky.cronos.infrastructure.persistence.core.CategoryRepository;
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

@Service @Slf4j
@RequiredArgsConstructor
public class CategoryServiceImplementation implements CategoryService {

    private final CategoryRepository categoryRepository;

    /**
     * Creates a new category
     */
    @Transactional
    @Override
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        if (categoryRepository.existsByName(request.name())) {
            throw new DuplicateResourceException("Category already exists");
        }

        Category category = Category.builder().name(request.name().trim())
                .description(request.description().trim()).build();

        category = categoryRepository.save(category);

        log.info("Category created: {} ", category.getName());

        return mapToResponse(category);
    }

    /**
     * Update a new category
     */
    @Transactional
    @Override
    public CategoryResponse updateCategory(Long id, UpdateCategoryRequest request) {

        Category category = categoryRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + id ));

        if (Boolean.TRUE.equals(category.getIsSystemDefault())) {
            throw new SystemResourceException("System default category cannot be edited");
        }

        if (!category.getName().equalsIgnoreCase(request.name().trim()) && categoryRepository.existsByName(request.name().trim())) {
            throw new DuplicateResourceException("Category name already exists");
        }

        category.setName(request.name().trim());
        category.setDescription(request.description().trim());

        category = categoryRepository.save(category);
        log.info("Category updated: ID {}, Name: {}", category.getId(), category.getName());

        return mapToResponse(category);
    }

    /**
     * Gets all categories available for a user (system + user's own) - paginated
     */
    @Transactional(readOnly = true)
    @Override
    public Page<CategoryResponse> getUserCategories(Pageable pageable) {
        log.debug("Fetching paginated Categories by User. Page: {}, Size: {}", pageable.getPageNumber(), pageable.getPageSize());
        return categoryRepository.findAll(pageable).map(this::mapToResponse);
    }

    /**
     * Gets system categories paginated
     */
    @Transactional(readOnly = true)
    @Override
    public Page<CategoryResponse> getSystemCategories(Pageable pageable) {
        log.debug("Fetching paginated Categories by System. Page: {}, Size: {}", pageable.getPageNumber(), pageable.getPageSize());
        return categoryRepository.findSystemCategories(pageable).map(this::mapToResponse);
    }

    /**
     * Imports System Categories
     */
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

                Optional<Category> existingCategory = categoryRepository.findByName(name);

                if (existingCategory.isPresent()) {
                    Category category = existingCategory.get();
                    category.setDescription(description);
                    categoryRepository.save(category);
                    updated.add(name);
                } else {
                    Category newCategory = Category.builder().name(name).description(description).isSystemDefault(true).build();
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

    @Transactional
    public void changeStatus(Long id, ChangeStatusRequest request) {
        log.info("Updating the status of Category with ID {} to {}", id, request.status());

        int updatedRows = categoryRepository.updateStatus(id, request.status());

        if (updatedRows == 0) {
            log.warn("Attempt to change the status of a non-existent Category: {}", id);
            throw new ResourceNotFoundException("El Tipo de Unidad con ID " + id + " no existe.");
        }
    }

    private CategoryResponse mapToResponse(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .isSystemDefault(category.getIsSystemDefault())
                .status(category.getStatus().name())
                .build();
    }
}
