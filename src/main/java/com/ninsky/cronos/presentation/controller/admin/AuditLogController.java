package com.ninsky.cronos.presentation.controller.admin;

import com.ninsky.cronos.application.response.audit.AuditLogResponse;
import com.ninsky.cronos.application.response.core.ApiResponse;
import com.ninsky.cronos.application.service.audit.AuditLogQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/audit-log")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Admin - Audit Log", description = "Read-only access to the immutable admin/security audit ledger")
public class AuditLogController {

    private final AuditLogQueryService auditLogQueryService;

    @GetMapping
    @Operation(summary = "Search audit log", description = "Paginated audit trail of admin actions, optionally filtered by target")
    public ResponseEntity<ApiResponse<Page<AuditLogResponse>>> search(
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetId,
            Pageable pageable) {
        Page<AuditLogResponse> entries = auditLogQueryService.search(targetType, targetId, pageable);
        return ResponseEntity.ok(ApiResponse.success("Audit log retrieved successfully", entries));
    }
}
