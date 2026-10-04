package com.ninsky.cronos.presentation.controller.admin;

import com.ninsky.cronos.application.imports.ImportBatchSummary;
import com.ninsky.cronos.application.imports.ImportHistoryService;
import com.ninsky.cronos.application.imports.ImportReport;
import com.ninsky.cronos.application.response.base.PaginatedResponse;
import com.ninsky.cronos.application.response.core.ApiResponse;
import com.ninsky.cronos.domain.model.imports.ImportResource;
import com.ninsky.cronos.domain.model.imports.ImportStatus;
import com.ninsky.cronos.presentation.controller.support.CatalogAccess;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/data-imports")
@PreAuthorize(CatalogAccess.CAN_MANAGE)
@Tag(name = "Data Imports", description = "Append-only ledger of every bulk import attempt (validated, committed, rejected, failed)")
public class DataImportController {

    private final ImportHistoryService importHistoryService;

    @GetMapping
    @Operation(summary = "Import history, newest first", description = "Optional filters: resource (UNIT_TYPE, MEASUREMENT_UNIT), status.")
    public ResponseEntity<ApiResponse<PaginatedResponse<ImportBatchSummary>>> history(
            @RequestParam(required = false) ImportResource resource,
            @RequestParam(required = false) ImportStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(PaginatedResponse.fromPage(importHistoryService.history(resource, status, pageable))));
    }

    @GetMapping("/{batchId}")
    @Operation(summary = "Full report of one import, exactly as returned at the time")
    public ResponseEntity<ApiResponse<ImportReport>> report(@PathVariable UUID batchId) {
        return ResponseEntity.ok(ApiResponse.success(importHistoryService.report(batchId)));
    }
}
