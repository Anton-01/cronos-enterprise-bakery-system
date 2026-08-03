package com.ninsky.cronos.presentation.controller.core;

import com.ninsky.cronos.application.request.core.CreateCategoryRequest;
import com.ninsky.cronos.application.request.core.UpdateCategoryRequest;
import com.ninsky.cronos.application.request.status.ChangeStatusRequest;
import com.ninsky.cronos.application.response.base.PaginatedResponse;
import com.ninsky.cronos.application.response.core.CategoryResponse;
import com.ninsky.cronos.application.response.core.ApiResponse;
import com.ninsky.cronos.application.response.imports.core.CsvImportResponse;
import com.ninsky.cronos.application.service.CategoryService;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Tag(name = "Categories", description = "Category management endpoints")
@RequiredArgsConstructor @Slf4j
@RequestMapping("/category")
public class CategoryController {

    private final CategoryService categoryService;

    @PostMapping
    @Operation(summary = "Create new category")
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        log.info("Create new category {}", request.name());
        CategoryResponse response = categoryService.createCategory(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Category created successfully", response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a not category system")
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(@Valid @RequestBody UpdateCategoryRequest request, @PathVariable Long id) {
        log.info("Update category {}", request.name());
        CategoryResponse response = categoryService.updateCategory(id, request);
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success("Category updated successfully", response));
    }

    @GetMapping
    @Operation(summary = "Get all categories paginated")
    public ResponseEntity<ApiResponse<PaginatedResponse<CategoryResponse>>> getUserCategories(@PageableDefault(page = 0, size = 10, sort = "id") Pageable pageable) {
        log.info("Get all categories paginated {}", pageable);
        Page<CategoryResponse> categories = categoryService.getUserCategories(pageable);
        PaginatedResponse<CategoryResponse> response = PaginatedResponse.fromPage(categories);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/system")
    @Operation(summary = "Get system categories paginated")
    public ResponseEntity<ApiResponse<PaginatedResponse<CategoryResponse>>> getSystemCategories(@PageableDefault(page = 0, size = 10, sort = "id") Pageable pageable) {
        log.info("Get system categories paginated {}", pageable);
        Page<CategoryResponse> categories = categoryService.getSystemCategories(pageable);
        PaginatedResponse<CategoryResponse> response = PaginatedResponse.fromPage(categories);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Import categories from a CSV file")
    @PostMapping(value = "/import", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<CsvImportResponse>> importCsv(@RequestParam("file") MultipartFile file) {
        log.info("Import categories from a CSV file {}", file.getOriginalFilename());
        CsvImportResponse result = categoryService.importCategoriesFromCsv(file);
        return ResponseEntity.ok(ApiResponse.success("Category CSV import completed", result));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Change Status", description = "Update only the status (ACTIVE, INACTIVE) of the Category.")
    public ResponseEntity<ApiResponse<Void>> changeStatus(@PathVariable Long id, @Valid @RequestBody ChangeStatusRequest request) {
        categoryService.changeStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success("Estatus de la Categoría actualizado correctamente a " + request.status(), null));
    }
}
