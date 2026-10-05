package com.ninsky.cronos.iam.audit.api;

import com.ninsky.cronos.application.response.core.ApiResponse;
import com.ninsky.cronos.domain.model.audit.AuditCategory;
import com.ninsky.cronos.domain.model.audit.AuditOutcome;
import com.ninsky.cronos.domain.model.audit.AuditSeverity;
import com.ninsky.cronos.iam.audit.query.AuditEventFilter;
import com.ninsky.cronos.iam.audit.query.AuditEventQueryService;
import com.ninsky.cronos.iam.audit.query.AuditEventView;
import com.ninsky.cronos.iam.permission.Authorities;
import com.ninsky.cronos.infrastructure.web.StrictApiContract;
import com.ninsky.cronos.infrastructure.web.paging.CatalogPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Audit log (spec §7.3): newest first, fixed sort. */
@RestController
@StrictApiContract
@RequiredArgsConstructor
@RequestMapping("/iam/audit-events")
@Tag(name = "IAM · Audit log", description = "Immutable ledger of security and administration events")
public class AuditEventController {

    private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final AuditEventQueryService audit;

    @GetMapping
    @PreAuthorize(Authorities.IAM_AUDIT_READ)
    @Operation(summary = "Search audit events", description = "from/to inclusive ISO instants, at most 366 days apart.")
    public ResponseEntity<ApiResponse<CatalogPage<AuditEventView>>> search(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) List<AuditCategory> categories,
            @RequestParam(required = false) List<AuditOutcome> outcomes,
            @RequestParam(required = false) List<AuditSeverity> severities,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        AuditEventFilter filter = new AuditEventFilter(search, categories, outcomes, severities, actorId, targetType, targetId, from, to);
        return ResponseEntity.ok(ApiResponse.success(audit.search(filter, page, size, LocaleContextHolder.getLocale())));
    }

    @GetMapping("/export")
    @PreAuthorize(Authorities.IAM_AUDIT_EXPORT)
    @Operation(summary = "Export audit events as CSV", description = "Same filters; max 100 000 rows; 5 exports per 10 minutes.")
    public ResponseEntity<StreamingResponseBody> export(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) List<AuditCategory> categories,
            @RequestParam(required = false) List<AuditOutcome> outcomes,
            @RequestParam(required = false) List<AuditSeverity> severities,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        AuditEventFilter filter = new AuditEventFilter(search, categories, outcomes, severities, actorId, targetType, targetId, from, to);
        AuditEventQueryService.Export export = audit.export(filter, LocaleContextHolder.getLocale());
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(export.fileName()).build().toString())
                .body(export.body());
    }
}
