package com.ninsky.cronos.application.service;

import com.ninsky.cronos.application.request.core.CreateCategoryRequest;
import com.ninsky.cronos.application.request.core.UpdateCategoryRequest;
import com.ninsky.cronos.application.response.core.CategoryResponse;
import com.ninsky.cronos.application.response.imports.core.CsvImportResponse;
import com.ninsky.cronos.domain.entity.enums.CategoryType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface CategoryService {

    CategoryResponse createCategory(CreateCategoryRequest request, UUID currentUserId);

    /** SYSTEM categories ∪ the caller's own USER categories (business rule 1), optionally filtered by type. */
    Page<CategoryResponse> getVisibleCategories(CategoryType type, Pageable pageable, UUID currentUserId);

    /** SYSTEM categories only, optionally filtered by type. */
    Page<CategoryResponse> getSystemCategories(CategoryType type, Pageable pageable);

    /** Rejects with {@link com.ninsky.cronos.infrastructure.exception.SystemResourceException} on a SYSTEM row,
     *  or {@link com.ninsky.cronos.infrastructure.exception.UnauthorizedCategoryModificationException} on another user's row. */
    CategoryResponse updateCategory(Long id, UpdateCategoryRequest request, UUID currentUserId);

    /** Same ownership checks as {@link #updateCategory}; soft-deletes (status -> TRASHED) on success. */
    void trashCategory(Long id, UUID currentUserId);

    /** Bulk-creates/updates SYSTEM categories only — see {@code CategoryServiceImplementation} for why. */
    CsvImportResponse importCategoriesFromCsv(MultipartFile file);
}
