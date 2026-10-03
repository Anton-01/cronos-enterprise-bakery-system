package com.ninsky.cronos.presentation.controller.core;

import com.ninsky.cronos.application.request.core.AllergenRequest;
import com.ninsky.cronos.application.request.status.ChangeStatusRequest;
import com.ninsky.cronos.application.response.base.PaginatedResponse;
import com.ninsky.cronos.application.response.core.AllergenResponse;
import com.ninsky.cronos.application.response.core.ApiResponse;
import com.ninsky.cronos.application.response.imports.core.CsvImportResponse;
import com.ninsky.cronos.application.service.AllergenService;
import com.ninsky.cronos.infrastructure.security.CronosUserPrincipal;
import com.ninsky.cronos.presentation.controller.support.CatalogAccess;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Allergens have no owner: every row is shared by all users (and {@code create} produces system
 * rows), so every write here is catalog maintenance, restricted like the unit catalog.
 */
@RestController
@Tag(name = "Allergens", description = "Allergen management endpoints")
@RequiredArgsConstructor
@RequestMapping("/allergen")
public class AllergenController {

    private final AllergenService allergenService;

    @PostMapping
    @PreAuthorize(CatalogAccess.CAN_MANAGE)
    @Operation(summary = "Create new allergen (catalog managers only)")
    public ResponseEntity<ApiResponse<AllergenResponse>> createAllergen(@Valid @RequestBody AllergenRequest request, Authentication authentication) {
        AllergenResponse response = allergenService.createAllergen(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Allergen created successfully", response));
    }

    @PutMapping("/{id}")
    @PreAuthorize(CatalogAccess.CAN_MANAGE)
    @Operation(summary = "Update a non-system allergen (catalog managers only)")
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

    @PreAuthorize(CatalogAccess.CAN_MANAGE)
    @Operation(summary = "Bulk-import system allergens from a CSV file (catalog managers only)",
            description = "UTF-8 CSV, columns: name, alternativeName, description. All-or-nothing: any invalid row "
                    + "rejects the file with 400 and one error per problem (line + column).")
    @PostMapping(value = "/import", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<CsvImportResponse>> importCsv(@RequestParam("file") MultipartFile file,
                                                                    @AuthenticationPrincipal CronosUserPrincipal principal) {
        CsvImportResponse result = allergenService.importAllergensFromCsv(file, CatalogAccess.actorOf(principal));
        return ResponseEntity.ok(ApiResponse.success("Allergen CSV import completed", result));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize(CatalogAccess.CAN_MANAGE)
    @Operation(summary = "Change Status (catalog managers only)", description = "Update only the status (ACTIVE, INACTIVE) of the Allergen.")
    public ResponseEntity<ApiResponse<Void>> changeStatus(@PathVariable UUID id, @Valid @RequestBody ChangeStatusRequest request) {
        allergenService.changeStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success("Estatus del Alérgeno actualizado correctamente a " + request.status(), null));
    }
}
