package com.ninsky.cronos.presentation.controller.core;

import com.ninsky.cronos.application.request.core.CreateRawMaterialRequest;
import com.ninsky.cronos.application.request.core.UpdateRawMaterialRequest;
import com.ninsky.cronos.application.request.status.ChangeStatusRequest;
import com.ninsky.cronos.application.response.base.PaginatedResponse;
import com.ninsky.cronos.application.response.core.ApiResponse;
import com.ninsky.cronos.application.response.core.RawMaterialListResponse;
import com.ninsky.cronos.application.response.core.RawMaterialResponse;
import com.ninsky.cronos.application.service.RawMaterialService;
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
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@Slf4j
@RestController
@Tag(name = "RawMaterial", description = "RawMaterial management endpoints")
@RequiredArgsConstructor
@RequestMapping("/raw-material")
public class RawMaterialController {

    private final RawMaterialService rawMaterialService;

    @GetMapping
    @Operation(summary = "Get all Raw Materials system paginated")
    public ResponseEntity<ApiResponse<PaginatedResponse<RawMaterialListResponse>>> getAllUnitTypes(@PageableDefault(page = 0, size = 10, sort = "id") Pageable pageable, Authentication authentication) {
        log.info("Fetching all Raw Materials system paginated");
        Page<RawMaterialListResponse> unitsPage = rawMaterialService.getUserRawMaterials(pageable,  authentication.getName());
        PaginatedResponse<RawMaterialListResponse> response = PaginatedResponse.fromPage(unitsPage);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get Raw Materials system by ID")
    public ResponseEntity<ApiResponse<RawMaterialResponse>> getRawMaterialById(@PathVariable UUID id) {
        log.info("Get Raw Materials system by ID: {}", id);
        RawMaterialResponse rawMaterial = rawMaterialService.getRawMaterialById(id);
        return ResponseEntity.ok(ApiResponse.success(rawMaterial));
    }

    @PostMapping
    @Operation(summary = "Create new raw material")
    public ResponseEntity<ApiResponse<RawMaterialResponse>> createRawMaterial(@Valid @RequestBody CreateRawMaterialRequest request, Authentication authentication) {
        log.info("Create new raw material request: {}", request.name());
        RawMaterialResponse response = rawMaterialService.createRawMaterial(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Raw Material created successfully", response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update existing raw material")
    public ResponseEntity<ApiResponse<RawMaterialResponse>> updateRawMaterial(@Valid @RequestBody UpdateRawMaterialRequest request, @PathVariable UUID id, Authentication authentication) {
        log.info("Update raw material request for name: {}", request.name());
        RawMaterialResponse response = rawMaterialService.updateRawMaterial(id, request, authentication.getName());
        return ResponseEntity.status(HttpStatus.OK) .body(ApiResponse.success("Raw Material updated successfully", response));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Change Status", description = "Update only the status (ACTIVE, INACTIVE) of the RawMaterial.")
    public ResponseEntity<ApiResponse<Void>> changeStatus(@PathVariable UUID id, @Valid @RequestBody ChangeStatusRequest request) {
        rawMaterialService.changeStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success("Estatus del Ingrediente actualizado correctamente a " + request.status(), null));
    }
}
