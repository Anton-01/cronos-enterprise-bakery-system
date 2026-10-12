package com.ninsky.cronos.calendar.api;

import com.ninsky.cronos.application.response.core.ApiResponse;
import com.ninsky.cronos.calendar.application.CalendarService;
import com.ninsky.cronos.calendar.domain.CalendarEventStatus;
import com.ninsky.cronos.calendar.domain.CalendarEventType;
import com.ninsky.cronos.iam.permission.Authorities;
import com.ninsky.cronos.iam.shared.TenantTime;
import com.ninsky.cronos.infrastructure.web.StrictApiContract;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Orders calendar of the caller (§5.3); every entry is owner-scoped. */
@RestController
@StrictApiContract
@RequestMapping("/calendar")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Calendar", description = "Orders, production and tastings; reminders and .ics export")
public class CalendarController {

    static final MediaType TEXT_CALENDAR = new MediaType("text", "calendar", StandardCharsets.UTF_8);

    private final CalendarService calendar;

    public CalendarController(CalendarService calendar) {
        this.calendar = calendar;
    }

    @GetMapping("/events")
    @PreAuthorize(Authorities.CALENDAR_EVENT_READ)
    @Operation(summary = "Entries in a range",
            description = "Overlap query start < to AND end > from; at most 62 days and 500 entries; ordered all-day first, then start and title.")
    public ResponseEntity<ApiResponse<List<CalendarEventResponse>>> list(
            @RequestParam Instant from, @RequestParam Instant to, @RequestParam(required = false) Set<CalendarEventType> types,
            @RequestParam(required = false) Set<CalendarEventStatus> statuses, @RequestParam(required = false) String search) {
        return ResponseEntity.ok(ApiResponse.success(null, calendar.list(from, to, types, statuses, search)));
    }

    @GetMapping("/events/upcoming")
    @PreAuthorize(Authorities.CALENDAR_EVENT_READ)
    @Operation(summary = "Next entries", description = "Active entries that have not ended, soonest first (limit 1–20)")
    public ResponseEntity<ApiResponse<List<CalendarEventResponse>>> upcoming(@RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(ApiResponse.success(null, calendar.upcoming(limit)));
    }

    @GetMapping("/events/{id}")
    @PreAuthorize(Authorities.CALENDAR_EVENT_READ)
    @Operation(summary = "One entry")
    public ResponseEntity<ApiResponse<CalendarEventResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(null, calendar.get(id)));
    }

    @PostMapping("/events")
    @PreAuthorize(Authorities.CALENDAR_EVENT_MANAGE)
    @Operation(summary = "Create an entry", description = "TENTATIVE unless CONFIRMED is given; quoteId must be an ACCEPTED quote of the caller")
    public ResponseEntity<ApiResponse<CalendarEventResponse>> create(@Valid @RequestBody CalendarEventRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(null, calendar.create(request)));
    }

    @PutMapping("/events/{id}")
    @PreAuthorize(Authorities.CALENDAR_EVENT_MANAGE)
    @Operation(summary = "Edit an entry", description = "Requires version; status and quote link are not changed here")
    public ResponseEntity<ApiResponse<CalendarEventResponse>> update(@PathVariable UUID id, @Valid @RequestBody CalendarEventRequest request) {
        return ResponseEntity.ok(ApiResponse.success(null, calendar.update(id, request)));
    }

    @PatchMapping("/events/{id}/schedule")
    @PreAuthorize(Authorities.CALENDAR_EVENT_MANAGE)
    @Operation(summary = "Reschedule", description = "Drag-to-reschedule; the production start and the reminders move along")
    public ResponseEntity<ApiResponse<CalendarEventResponse>> reschedule(@PathVariable UUID id, @Valid @RequestBody RescheduleRequest request) {
        return ResponseEntity.ok(ApiResponse.success(null, calendar.reschedule(id, request)));
    }

    @PostMapping("/events/{id}/status")
    @PreAuthorize(Authorities.CALENDAR_EVENT_MANAGE)
    @Operation(summary = "Change the status", description = "TENTATIVE→CONFIRMED→IN_PRODUCTION→READY→DELIVERED; any active status → CANCELLED → TENTATIVE")
    public ResponseEntity<ApiResponse<CalendarEventResponse>> status(@PathVariable UUID id, @Valid @RequestBody StatusChangeRequest request) {
        return ResponseEntity.ok(ApiResponse.success(null, calendar.changeStatus(id, request)));
    }

    @DeleteMapping("/events/{id}")
    @PreAuthorize(Authorities.CALENDAR_EVENT_MANAGE)
    @Operation(summary = "Delete an entry", description = "Soft delete; a linked quote is unlinked")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        calendar.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null, null));
    }

    @GetMapping("/summary")
    @PreAuthorize(Authorities.CALENDAR_EVENT_READ)
    @Operation(summary = "Figures of a range")
    public ResponseEntity<ApiResponse<CalendarSummary>> summary(@RequestParam Instant from, @RequestParam Instant to) {
        return ResponseEntity.ok(ApiResponse.success(null, calendar.summary(from, to)));
    }

    @GetMapping("/export.ics")
    @PreAuthorize(Authorities.CALENDAR_EVENT_READ)
    @Operation(summary = "iCalendar export", description = "RFC 5545 document of the entries in the range")
    public ResponseEntity<byte[]> export(@RequestParam Instant from, @RequestParam Instant to) {
        byte[] body = calendar.export(from, to).getBytes(StandardCharsets.UTF_8);
        String fileName = "cronos-" + from.atZone(TenantTime.ZONE).toLocalDate() + ".ics";
        return ResponseEntity.ok()
                .contentType(TEXT_CALENDAR)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
                .body(body);
    }
}
