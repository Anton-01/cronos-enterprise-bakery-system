package com.ninsky.cronos.presentation.controller.core;

import com.ninsky.cronos.application.request.core.CreateMeasurementUnitRequest;
import com.ninsky.cronos.application.request.core.UpdateMeasurementUnitRequest;
import com.ninsky.cronos.application.request.status.ChangeStatusRequest;
import com.ninsky.cronos.application.response.base.PaginatedResponse;
import com.ninsky.cronos.application.response.core.ApiResponse;
import com.ninsky.cronos.application.response.core.MeasurementUnitResponse;
import com.ninsky.cronos.application.service.MeasurementUnitService;
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

@Slf4j
@RestController
@Tag(name = "MeasurementUnit", description = "MeasurementUnit management endpoints")
@RequiredArgsConstructor
@RequestMapping("/measurement-unit")
public class MeasurementUnitController {

    private final MeasurementUnitService measurementUnitService;

    @PostMapping
    @Operation(summary = "Create new measurementUnit")
    public ResponseEntity<ApiResponse<MeasurementUnitResponse>> createUnitType(@Valid @RequestBody CreateMeasurementUnitRequest request, Authentication authentication) {
        log.info("Create new measurementUnit name: {}", request.name());
        MeasurementUnitResponse response = measurementUnitService.createMeasurementUnit(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("MeasurementUnit created successfully", response));
    }

    @PutMapping
    @Operation(summary = "Update an existing measurementUnit")
    public ResponseEntity<ApiResponse<MeasurementUnitResponse>> updateUnitType(@Valid @RequestBody UpdateMeasurementUnitRequest request, Authentication authentication) {
        log.info("Updating measurementUnit :: request : {}", request.name());
        MeasurementUnitResponse response = measurementUnitService.updateMeasurementUnit(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success("MeasurementUnit updated successfully", response));
    }

    @GetMapping("/system")
    @Operation(summary = "Get all measurementUnit system paginated")
    public ResponseEntity<ApiResponse<PaginatedResponse<MeasurementUnitResponse>>> getAllUnitTypes(@PageableDefault(page = 0, size = 10, sort = "id") Pageable pageable, Authentication authentication) {
        log.info("Fetching all measurementUnit system paginated");
        Page<MeasurementUnitResponse> unitsPage = measurementUnitService.getSystemMeasurementUnits(pageable, authentication.getName());
        PaginatedResponse<MeasurementUnitResponse> response = PaginatedResponse.fromPage(unitsPage);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Change Status", description = "Update only the status (ACTIVE, INACTIVE) of the MeasurementUnit.")
    public ResponseEntity<ApiResponse<Void>> changeStatus(@PathVariable Long id, @Valid @RequestBody ChangeStatusRequest request) {
        measurementUnitService.changeStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success("Estatus de la Unidad de Medida actualizado correctamente a " + request.status(), null));
    }
}
