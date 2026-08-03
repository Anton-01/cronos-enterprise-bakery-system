package com.ninsky.cronos.presentation.controller.core;

import com.ninsky.cronos.application.request.core.UnitTypeRequest;
import com.ninsky.cronos.application.request.status.ChangeStatusRequest;
import com.ninsky.cronos.application.response.base.PaginatedResponse;
import com.ninsky.cronos.application.response.core.ApiResponse;
import com.ninsky.cronos.application.response.core.UnitTypeResponse;
import com.ninsky.cronos.application.service.UnitTypeService;
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

@RestController
@Tag(name = "Categories", description = "UnitType management endpoints")
@RequiredArgsConstructor @Slf4j
@RequestMapping("/unit-type")
public class UnitTypeController {

    private final UnitTypeService unitTypeService;

    @PostMapping
    @Operation(summary = "Create new unitType")
    public ResponseEntity<ApiResponse<UnitTypeResponse>> createUnitType(@Valid @RequestBody UnitTypeRequest request) {
        log.info("Create new unitType request {}", request.name());
        UnitTypeResponse response = unitTypeService.createUnitType(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("UnitType created successfully", response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing unitType")
    public ResponseEntity<ApiResponse<UnitTypeResponse>> updateUnitType(@Valid @RequestBody UnitTypeRequest request, Authentication authentication, @PathVariable Long id) {
        log.info("Update an existing unitType {}", request.name());
        UnitTypeResponse response = unitTypeService.updateUnitType(authentication.getName(), id, request);
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success("UnitType updated successfully", response));
    }

    @GetMapping
    @Operation(summary = "Get all unitTypes paginated")
    public ResponseEntity<ApiResponse<PaginatedResponse<UnitTypeResponse>>> getAllUnitTypes(@PageableDefault(page = 0, size = 10, sort = "id") Pageable pageable) {
        log.info("Request received to get all UnitTypes");
        Page<UnitTypeResponse> unitsPage = unitTypeService.getUnitTypes(pageable);
        PaginatedResponse<UnitTypeResponse> response = PaginatedResponse.fromPage(unitsPage);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete unitType (Soft Delete)")
    public ResponseEntity<ApiResponse<Void>> deleteUnitType(@PathVariable Long id) {
        log.info("Request received to soft-delete UnitType with ID: {}", id);
        unitTypeService.deleteUnitType(id);
        return ResponseEntity.ok(ApiResponse.success("UnitType deleted successfully", null));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Change Status", description = "Update only the status (ACTIVE, INACTIVE) of the UnitType.")
    public ResponseEntity<ApiResponse<Void>> changeStatus(@PathVariable Long id, @Valid @RequestBody ChangeStatusRequest request) {
        unitTypeService.changeStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success("Estatus del Tipo de Unidad actualizado correctamente a " + request.status(), null));
    }
}
