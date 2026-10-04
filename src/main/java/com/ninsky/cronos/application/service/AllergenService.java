package com.ninsky.cronos.application.service;

import com.ninsky.cronos.application.request.core.AllergenRequest;
import com.ninsky.cronos.application.request.status.ChangeStatusRequest;
import com.ninsky.cronos.application.response.core.AllergenResponse;
import com.ninsky.cronos.application.response.imports.core.CsvImportResponse;
import com.ninsky.cronos.domain.model.audit.Actor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface AllergenService {
    AllergenResponse createAllergen(AllergenRequest request, String username);
    Page<AllergenResponse> getUserAllergens(Pageable pageable);
    Page<AllergenResponse> getSystemAllergens(Pageable pageable);
    AllergenResponse updateAllergen(UUID allergenId, AllergenRequest request, String username);
    /** All-or-nothing; throws {@link com.ninsky.cronos.infrastructure.exception.CsvImportRejectedException} listing every invalid row. */
    CsvImportResponse importAllergensFromCsv(MultipartFile file, Actor actor);
    void changeStatus(UUID id, ChangeStatusRequest request);
}