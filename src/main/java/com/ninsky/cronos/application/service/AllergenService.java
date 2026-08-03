package com.ninsky.cronos.application.service;

import com.ninsky.cronos.application.request.core.AllergenRequest;
import com.ninsky.cronos.application.request.status.ChangeStatusRequest;
import com.ninsky.cronos.application.response.core.AllergenResponse;
import com.ninsky.cronos.application.response.imports.core.CsvImportResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface AllergenService {
    AllergenResponse createAllergen(AllergenRequest request, String username);
    Page<AllergenResponse> getUserAllergens(Pageable pageable);
    Page<AllergenResponse> getSystemAllergens(Pageable pageable);
    AllergenResponse updateAllergen(UUID allergenId, AllergenRequest request, String username);
    CsvImportResponse importAllergensFromCsv(MultipartFile file);
    void changeStatus(UUID id, ChangeStatusRequest request);
}