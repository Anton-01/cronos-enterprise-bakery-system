package com.ninsky.cronos.presentation.controller.core;

import com.ninsky.cronos.application.imports.CatalogImportService;
import com.ninsky.cronos.application.imports.ImportReport;
import com.ninsky.cronos.application.request.core.UnitTypeRequest;
import com.ninsky.cronos.application.request.status.ChangeStatusRequest;
import com.ninsky.cronos.application.response.base.PaginatedResponse;
import com.ninsky.cronos.application.response.core.ApiResponse;
import com.ninsky.cronos.application.response.core.UnitTypeResponse;
import com.ninsky.cronos.application.service.UnitTypeService;
import com.ninsky.cronos.domain.entity.enums.RecordStatus;
import com.ninsky.cronos.domain.entity.enums.UnitDimension;
import com.ninsky.cronos.domain.model.imports.ImportResource;
import com.ninsky.cronos.domain.port.core.UnitTypeSearchCriteria;
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
@RequestMapping("/unit-type")
@Tag(name = "Unit Types", description = "System catalog of unit types (one per physical dimension: MASS, VOLUME, COUNT, LENGTH)")
public class UnitTypeController {

    private final UnitTypeService unitTypeService;
    private final CatalogImportService catalogImportService;

    @PostMapping
    @PreAuthorize(CatalogAccess.UNIT_TYPE_MANAGE)
    @Operation(summary = "Create a unit type")
    public ResponseEntity<ApiResponse<UnitTypeResponse>> createUnitType(@Valid @RequestBody UnitTypeRequest request,
                                                                        @AuthenticationPrincipal CronosUserPrincipal principal) {
        UnitTypeResponse response = unitTypeService.createUnitType(request, CatalogAccess.actorOf(principal));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("UnitType created successfully", response));
    }

    @PutMapping("/{id}")
    @PreAuthorize(CatalogAccess.UNIT_TYPE_MANAGE)
    @Operation(summary = "Update a unit type", description = "The dimension is locked once the unit type has measurement units.")
    public ResponseEntity<ApiResponse<UnitTypeResponse>> updateUnitType(@PathVariable Long id, @Valid @RequestBody UnitTypeRequest request,
                                                                        @AuthenticationPrincipal CronosUserPrincipal principal) {
        UnitTypeResponse response = unitTypeService.updateUnitType(id, request, CatalogAccess.actorOf(principal));
        return ResponseEntity.ok(ApiResponse.success("UnitType updated successfully", response));
    }

    @GetMapping
    @Operation(summary = "Search unit types (paginated)",
            description = "Optional filters: search (code/name contains), dimension, status. Sortable by id, codeIdentity, name, dimension, status, createdAt, updatedAt.")
    public ResponseEntity<ApiResponse<PaginatedResponse<UnitTypeResponse>>> searchUnitTypes(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UnitDimension dimension,
            @RequestParam(required = false) RecordStatus status,
            @PageableDefault(size = 10, sort = "name") Pageable pageable) {
        var page = unitTypeService.searchUnitTypes(new UnitTypeSearchCriteria(search, dimension, status), pageable);
        return ResponseEntity.ok(ApiResponse.success(PaginatedResponse.fromPage(page)));
    }

    @GetMapping("/catalog")
    @Operation(summary = "Active unit types (not paginated)", description = "Options for the unit-type picker of the measurement-unit form.")
    public ResponseEntity<ApiResponse<List<UnitTypeResponse>>> getActiveCatalog() {
        return ResponseEntity.ok(ApiResponse.success(unitTypeService.getActiveCatalog()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a unit type")
    public ResponseEntity<ApiResponse<UnitTypeResponse>> getUnitType(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(unitTypeService.getUnitType(id)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(CatalogAccess.UNIT_TYPE_MANAGE)
    @Operation(summary = "Delete a unit type (soft delete)", description = "Rejected while the unit type still has measurement units.")
    public ResponseEntity<ApiResponse<Void>> deleteUnitType(@PathVariable Long id, @AuthenticationPrincipal CronosUserPrincipal principal) {
        unitTypeService.deleteUnitType(id, CatalogAccess.actorOf(principal));
        return ResponseEntity.ok(ApiResponse.success("UnitType deleted successfully", null));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize(CatalogAccess.UNIT_TYPE_MANAGE)
    @Operation(summary = "Change status", description = "ACTIVE, INACTIVE or ARCHIVED. Deactivation is rejected while the type has active units.")
    public ResponseEntity<ApiResponse<Void>> changeStatus(@PathVariable Long id, @Valid @RequestBody ChangeStatusRequest request,
                                                          @AuthenticationPrincipal CronosUserPrincipal principal) {
        unitTypeService.changeStatus(id, request.status(), CatalogAccess.actorOf(principal));
        return ResponseEntity.ok(ApiResponse.success("UnitType status updated to " + request.status(), null));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(CatalogAccess.IMPORT_EXECUTE)
    @Operation(summary = "Bulk upsert unit types from .xlsx (sheet 'UnitTypes')",
            description = "All-or-nothing. dryRun=true (default) only validates; dryRun=false applies. The report's status is "
                    + "VALIDATED, COMMITTED or REJECTED; every attempt is recorded in the import ledger (GET /data-imports).")
    public ResponseEntity<ApiResponse<ImportReport>> importUnitTypes(
            @RequestPart("file") MultipartFile file,
            @Parameter(description = "true: validate only; false: validate and apply") @RequestParam(defaultValue = "true") boolean dryRun,
            @AuthenticationPrincipal CronosUserPrincipal principal, HttpServletRequest httpRequest) {
        log.info("Unit type import requested: file={}, size={}, dryRun={}", file.getOriginalFilename(), file.getSize(), dryRun);
        ImportReport report = catalogImportService.importCatalog(ImportResource.UNIT_TYPE, ImportEndpointSupport.toImportFile(file), dryRun,
                CatalogAccess.actorOf(principal), RequestLocaleResolver.resolve(httpRequest));
        return ResponseEntity.ok(ApiResponse.success("Import " + report.status(), report));
    }

    @GetMapping("/import/template")
    @PreAuthorize(CatalogAccess.IMPORT_EXECUTE)
    @Operation(summary = "Download the .xlsx template for unit type imports")
    public ResponseEntity<Resource> downloadTemplate() {
        return ImportEndpointSupport.template(ImportResource.UNIT_TYPE);
    }
}
