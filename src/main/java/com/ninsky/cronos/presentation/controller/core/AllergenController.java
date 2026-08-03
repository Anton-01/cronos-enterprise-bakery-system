package com.ninsky.cronos.presentation.controller.core;

import com.ninsky.cronos.application.request.core.AllergenRequest;
import com.ninsky.cronos.application.request.status.ChangeStatusRequest;
import com.ninsky.cronos.application.response.base.PaginatedResponse;
import com.ninsky.cronos.application.response.core.AllergenResponse;
import com.ninsky.cronos.application.response.core.ApiResponse;
import com.ninsky.cronos.application.response.imports.core.CsvImportResponse;
import com.ninsky.cronos.application.service.AllergenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@Tag(name = "Allergens", description = "Allergen management endpoints")
@RequiredArgsConstructor
@RequestMapping("/allergen")
public class AllergenController {

    private final AllergenService allergenService;

    @PostMapping
    @Operation(summary = "Create new allergen")
    public ResponseEntity<ApiResponse<AllergenResponse>> createAllergen(@Valid @RequestBody AllergenRequest request, Authentication authentication) {
        AllergenResponse response = allergenService.createAllergen(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Allergen created successfully", response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a not allergen system")
    public ResponseEntity<ApiResponse<AllergenResponse>> updateAllergen(@Valid @RequestBody AllergenRequest request, Authentication authentication, @PathVariable UUID id) {
        AllergenResponse response = allergenService.updateAllergen(id, request, authentication.getName());
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success("Allergen updated successfully", response));
    }

    @GetMapping
    @Operation(summary = "Get all allergens")
    public ResponseEntity<ApiResponse<PaginatedResponse<AllergenResponse>>> getUserAllergens(@PageableDefault(page = 0, size = 10, sort = "id") Pageable pageable) {
        Page<AllergenResponse> categories = allergenService.getUserAllergens(pageable);
        PaginatedResponse<AllergenResponse> response = PaginatedResponse.fromPage(categories);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/system")
    @Operation(summary = "Get system allergens")
    public ResponseEntity<ApiResponse<PaginatedResponse<AllergenResponse>>> getSystemAllergens(@PageableDefault(page = 0, size = 10, sort = "id") Pageable pageable) {
        Page<AllergenResponse> categories = allergenService.getSystemAllergens(pageable);
        PaginatedResponse<AllergenResponse> response = PaginatedResponse.fromPage(categories);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Import Allergens from a CSV file")
    @PostMapping(value = "/import", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<CsvImportResponse>> importCsv(@RequestParam("file") MultipartFile file) {
        CsvImportResponse result = allergenService.importAllergensFromCsv(file);
        return ResponseEntity.ok(ApiResponse.success("Allergen CSV import completed", result));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Change Status", description = "Update only the status (ACTIVE, INACTIVE) of the Allergen.")
    public ResponseEntity<ApiResponse<Void>> changeStatus(@PathVariable UUID id, @Valid @RequestBody ChangeStatusRequest request) {
        allergenService.changeStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success("Estatus del Alérgeno actualizado correctamente a " + request.status(), null));
    }
}
