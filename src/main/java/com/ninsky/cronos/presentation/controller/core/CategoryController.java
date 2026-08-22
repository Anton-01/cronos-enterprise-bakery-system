package com.ninsky.cronos.presentation.controller.core;

import com.ninsky.cronos.application.request.core.CreateCategoryRequest;
import com.ninsky.cronos.application.request.core.UpdateCategoryRequest;
import com.ninsky.cronos.application.response.base.PaginatedResponse;
import com.ninsky.cronos.application.response.core.ApiResponse;
import com.ninsky.cronos.application.response.core.CategoryResponse;
import com.ninsky.cronos.application.response.imports.core.CsvImportResponse;
import com.ninsky.cronos.application.service.CategoryService;
import com.ninsky.cronos.domain.entity.enums.CategoryType;
import com.ninsky.cronos.infrastructure.security.CronosUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Tag(name = "Categories", description = "Category management endpoints")
@RequiredArgsConstructor @Slf4j
@RequestMapping("/category")
public class CategoryController {

    private final CategoryService categoryService;

    @PostMapping
    @Operation(summary = "Create a new (USER-scoped) category")
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(
            @Valid @RequestBody CreateCategoryRequest request,
            @AuthenticationPrincipal CronosUserPrincipal principal) {
        log.info("Create new category {}", request.name());
        CategoryResponse response = categoryService.createCategory(request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Category created successfully", response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a category you own", description = "System categories and other users' categories are rejected.")
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(
            @PathVariable Long id, @Valid @RequestBody UpdateCategoryRequest request,
            @AuthenticationPrincipal CronosUserPrincipal principal) {
        log.info("Update category {}", id);
        CategoryResponse response = categoryService.updateCategory(id, request, principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Category updated successfully", response));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Trash a category you own (soft delete)", description = "System categories and other users' categories are rejected.")
    public ResponseEntity<ApiResponse<Void>> trashCategory(
            @PathVariable Long id, @AuthenticationPrincipal CronosUserPrincipal principal) {
        log.info("Trash category {}", id);
        categoryService.trashCategory(id, principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Category deleted successfully", null));
    }

    @GetMapping
    @Operation(summary = "Get categories visible to the caller",
            description = "All SYSTEM categories plus the caller's own USER categories. Filter by ?type=PRODUCT|INGREDIENT.")
    public ResponseEntity<ApiResponse<PaginatedResponse<CategoryResponse>>> getVisibleCategories(
            @RequestParam(required = false) CategoryType type,
            @PageableDefault(page = 0, size = 10, sort = "id") Pageable pageable,
            @AuthenticationPrincipal CronosUserPrincipal principal) {
        log.info("Get visible categories. type={}, page={}", type, pageable);
        Page<CategoryResponse> categories = categoryService.getVisibleCategories(type, pageable, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(PaginatedResponse.fromPage(categories)));
    }

    @GetMapping("/system")
    @Operation(summary = "Get SYSTEM categories only", description = "Filter by ?type=PRODUCT|INGREDIENT.")
    public ResponseEntity<ApiResponse<PaginatedResponse<CategoryResponse>>> getSystemCategories(
            @RequestParam(required = false) CategoryType type,
            @PageableDefault(page = 0, size = 10, sort = "id") Pageable pageable) {
        log.info("Get system categories. type={}, page={}", type, pageable);
        Page<CategoryResponse> categories = categoryService.getSystemCategories(type, pageable);
        return ResponseEntity.ok(ApiResponse.success(PaginatedResponse.fromPage(categories)));
    }

    @Operation(summary = "Bulk-import SYSTEM categories from a CSV file", description = "CSV columns: name, description, type (PRODUCT|INGREDIENT).")
    @PostMapping(value = "/import", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<CsvImportResponse>> importCsv(@RequestParam("file") MultipartFile file) {
        log.info("Import categories from a CSV file {}", file.getOriginalFilename());
        CsvImportResponse result = categoryService.importCategoriesFromCsv(file);
        return ResponseEntity.ok(ApiResponse.success("Category CSV import completed", result));
    }
}
