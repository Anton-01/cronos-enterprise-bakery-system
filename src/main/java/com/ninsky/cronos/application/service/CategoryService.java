package com.ninsky.cronos.application.service;

import com.ninsky.cronos.application.request.core.CreateCategoryRequest;
import com.ninsky.cronos.application.request.core.UpdateCategoryRequest;
import com.ninsky.cronos.application.request.status.ChangeStatusRequest;
import com.ninsky.cronos.application.response.core.CategoryResponse;
import com.ninsky.cronos.application.response.imports.core.CsvImportResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface CategoryService {
    CategoryResponse createCategory(CreateCategoryRequest request);
    Page<CategoryResponse> getUserCategories(Pageable pageable);
    Page<CategoryResponse> getSystemCategories(Pageable pageable);
    CategoryResponse updateCategory(Long id, UpdateCategoryRequest request);
    CsvImportResponse importCategoriesFromCsv(MultipartFile file);
    void changeStatus(Long id, ChangeStatusRequest request);
}
