package com.ninsky.cronos.presentation.controller.core;

import com.ninsky.cronos.application.imports.CatalogImportService;
import com.ninsky.cronos.application.imports.ImportReport;
import com.ninsky.cronos.application.request.core.MeasurementUnitRequest;
import com.ninsky.cronos.application.request.core.UnitConversionRequest;
import com.ninsky.cronos.application.request.status.ChangeStatusRequest;
import com.ninsky.cronos.application.response.base.PaginatedResponse;
import com.ninsky.cronos.application.response.core.ApiResponse;
import com.ninsky.cronos.application.response.core.MeasurementUnitOptionResponse;
import com.ninsky.cronos.application.response.core.MeasurementUnitResponse;
import com.ninsky.cronos.application.response.core.UnitConversionResponse;
import com.ninsky.cronos.application.service.MeasurementUnitService;
import com.ninsky.cronos.domain.entity.enums.RecordStatus;
import com.ninsky.cronos.domain.entity.enums.UnitDimension;
import com.ninsky.cronos.domain.model.imports.ImportResource;
import com.ninsky.cronos.domain.port.core.MeasurementUnitSearchCriteria;
import com.ninsky.cronos.infrastructure.security.CronosUserPrincipal;
import com.ninsky.cronos.infrastructure.web.RequestLocaleResolver;
import com.ninsky.cronos.presentation.controller.support.CatalogAccess;
import com.ninsky.cronos.presentation.controller.support.ImportEndpointSupport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/measurement-unit")
@Tag(name = "Measurement Units", description = "System catalog of measurement units and on-the-fly unit conversion")
public class MeasurementUnitController {

    private final MeasurementUnitService measurementUnitService;
    private final CatalogImportService catalogImportService;

    @PostMapping
    @PreAuthorize(CatalogAccess.CAN_MANAGE)
    @Operation(summary = "Create a measurement unit",
            description = "The first unit of a unit type must be its base unit (isBaseUnit=true, multiplierToBase=1).")
    public ResponseEntity<ApiResponse<MeasurementUnitResponse>> createMeasurementUnit(@Valid @RequestBody MeasurementUnitRequest request,
                                                                                      @AuthenticationPrincipal CronosUserPrincipal principal) {
        MeasurementUnitResponse response = measurementUnitService.createMeasurementUnit(request, CatalogAccess.actorOf(principal));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("MeasurementUnit created successfully", response));
    }

    @PutMapping("/{id}")
    @PreAuthorize(CatalogAccess.CAN_MANAGE)
    @Operation(summary = "Update a measurement unit",
            description = "When the unit is in use (inUse=true) its unitTypeId, multiplierToBase and isBaseUnit can no longer change.")
    public ResponseEntity<ApiResponse<MeasurementUnitResponse>> updateMeasurementUnit(@PathVariable Long id, @Valid @RequestBody MeasurementUnitRequest request,
                                                                                      @AuthenticationPrincipal CronosUserPrincipal principal) {
        MeasurementUnitResponse response = measurementUnitService.updateMeasurementUnit(id, request, CatalogAccess.actorOf(principal));
        return ResponseEntity.ok(ApiResponse.success("MeasurementUnit updated successfully", response));
    }

    @GetMapping
    @Operation(summary = "Search measurement units (paginated)",
            description = "Optional filters: search (code/name/plural contains), unitTypeId, dimension, status. Sortable by id, codeIdentity, "
                    + "name, namePlural, multiplierToBase, status, unitType, dimension, createdAt, updatedAt.")
    public ResponseEntity<ApiResponse<PaginatedResponse<MeasurementUnitResponse>>> searchMeasurementUnits(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long unitTypeId,
            @RequestParam(required = false) UnitDimension dimension,
            @RequestParam(required = false) RecordStatus status,
            @PageableDefault(size = 10, sort = "name") Pageable pageable) {
        var page = measurementUnitService.searchMeasurementUnits(new MeasurementUnitSearchCriteria(search, unitTypeId, dimension, status), pageable);
        return ResponseEntity.ok(ApiResponse.success(PaginatedResponse.fromPage(page)));
    }

    @GetMapping("/catalog")
    @Operation(summary = "Selectable units (not paginated)",
            description = "Active units of active unit types, ordered by unit type then size: the unit picker of recipes and raw materials.")
    public ResponseEntity<ApiResponse<List<MeasurementUnitOptionResponse>>> getSelectableCatalog() {
        return ResponseEntity.ok(ApiResponse.success(measurementUnitService.getSelectableCatalog()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a measurement unit")
    public ResponseEntity<ApiResponse<MeasurementUnitResponse>> getMeasurementUnit(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(measurementUnitService.getMeasurementUnit(id)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(CatalogAccess.CAN_MANAGE)
    @Operation(summary = "Delete a measurement unit (soft delete)",
            description = "Rejected for units in use, system-reserved units (g, cup, tbsp, tsp) and a base unit other units depend on.")
    public ResponseEntity<ApiResponse<Void>> deleteMeasurementUnit(@PathVariable Long id, @AuthenticationPrincipal CronosUserPrincipal principal) {
        measurementUnitService.deleteMeasurementUnit(id, CatalogAccess.actorOf(principal));
        return ResponseEntity.ok(ApiResponse.success("MeasurementUnit deleted successfully", null));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize(CatalogAccess.CAN_MANAGE)
    @Operation(summary = "Change status", description = "ACTIVE, INACTIVE or ARCHIVED. An inactive unit keeps converting existing data but can't be newly selected.")
    public ResponseEntity<ApiResponse<Void>> changeStatus(@PathVariable Long id, @Valid @RequestBody ChangeStatusRequest request,
                                                          @AuthenticationPrincipal CronosUserPrincipal principal) {
        measurementUnitService.changeStatus(id, request.status(), CatalogAccess.actorOf(principal));
        return ResponseEntity.ok(ApiResponse.success("MeasurementUnit status updated to " + request.status(), null));
    }

    @PostMapping("/convert")
    @Operation(summary = "Convert a quantity between two units (nothing is persisted)",
            description = "Same dimension: linear. MASS <-> VOLUME: needs rawMaterialId (one of yours) with a density rule. Other combinations: 409.")
    public ResponseEntity<ApiResponse<UnitConversionResponse>> convert(@Valid @RequestBody UnitConversionRequest request,
                                                                       @AuthenticationPrincipal CronosUserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(measurementUnitService.convert(request, CatalogAccess.actorOf(principal))));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(CatalogAccess.CAN_MANAGE)
    @Operation(summary = "Bulk upsert measurement units from .xlsx (sheet 'MeasurementUnits')",
            description = "Import unit types first: unitTypeCode must exist. All-or-nothing. dryRun=true (default) only validates; "
                    + "dryRun=false applies. Every attempt is recorded in the import ledger (GET /data-imports).")
    public ResponseEntity<ApiResponse<ImportReport>> importMeasurementUnits(
            @RequestPart("file") MultipartFile file,
            @Parameter(description = "true: validate only; false: validate and apply") @RequestParam(defaultValue = "true") boolean dryRun,
            @AuthenticationPrincipal CronosUserPrincipal principal, HttpServletRequest httpRequest) {
        log.info("Measurement unit import requested: file={}, size={}, dryRun={}", file.getOriginalFilename(), file.getSize(), dryRun);
        ImportReport report = catalogImportService.importCatalog(ImportResource.MEASUREMENT_UNIT, ImportEndpointSupport.toImportFile(file), dryRun,
                CatalogAccess.actorOf(principal), RequestLocaleResolver.resolve(httpRequest));
        return ResponseEntity.ok(ApiResponse.success("Import " + report.status(), report));
    }

    @GetMapping("/import/template")
    @PreAuthorize(CatalogAccess.CAN_MANAGE)
    @Operation(summary = "Download the .xlsx template for measurement unit imports")
    public ResponseEntity<Resource> downloadTemplate() {
        return ImportEndpointSupport.template(ImportResource.MEASUREMENT_UNIT);
    }
}
