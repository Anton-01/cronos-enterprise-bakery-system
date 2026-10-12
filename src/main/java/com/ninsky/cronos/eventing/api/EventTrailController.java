package com.ninsky.cronos.eventing.api;

import com.ninsky.cronos.application.response.core.ApiResponse;
import com.ninsky.cronos.eventing.application.EventTrailService;
import com.ninsky.cronos.iam.permission.Authorities;
import com.ninsky.cronos.iam.shared.ActorProvider;
import com.ninsky.cronos.infrastructure.web.StrictApiContract;
import com.ninsky.cronos.infrastructure.web.paging.CatalogPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/** Operational event trail (§8.6): what every request caused, down to each email and push. */
@RestController
@StrictApiContract
@RequestMapping("/admin/events")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin · Event trail", description = "Domain events, their handlers and deliveries (redacted)")
public class EventTrailController {

    private final EventTrailService service;
    private final ActorProvider actors;

    public EventTrailController(EventTrailService service, ActorProvider actors) {
        this.service = service;
        this.actors = actors;
    }

    @GetMapping
    @PreAuthorize(Authorities.IAM_AUDIT_READ)
    @Operation(summary = "Search events", description = "Newest first. status ∈ PENDING, DISPATCHED, FAILED, DEAD.")
    public ResponseEntity<ApiResponse<CatalogPage<EventTrailEntry>>> search(
            @RequestParam(required = false) String aggregateType,
            @RequestParam(required = false) UUID aggregateId,
            @RequestParam(required = false) UUID correlationId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        EventTrailFilter filter = new EventTrailFilter(aggregateType, aggregateId, correlationId, type, status, from, to);
        return ResponseEntity.ok(ApiResponse.success(null, service.search(filter, page, size)));
    }

    @GetMapping("/{id}/trail")
    @PreAuthorize(Authorities.IAM_AUDIT_READ)
    @Operation(summary = "Causes, effects and deliveries of one event")
    public ResponseEntity<ApiResponse<EventTrail>> trail(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(null, service.trail(id)));
    }

    @PostMapping("/{id}/retry")
    @PreAuthorize(Authorities.IAM_AUDIT_READ)
    @Operation(summary = "Re-queue a DEAD event", description = "SUPER_ADMIN only; the attempt budget starts over.")
    public ResponseEntity<ApiResponse<EventTrailEntry>> retry(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(null, service.retry(id, actors.require())));
    }
}
