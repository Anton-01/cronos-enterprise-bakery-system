package com.ninsky.cronos.presentation.controller.quote;

import org.springframework.security.access.prepost.PreAuthorize;
import com.ninsky.cronos.iam.permission.Authorities;
import com.ninsky.cronos.application.request.quote.CreateQuoteRequest;
import com.ninsky.cronos.application.response.core.ApiResponse;
import com.ninsky.cronos.kitchen.shared.Warned;
import com.ninsky.cronos.application.response.quote.BakerQuoteDetailResponse;
import com.ninsky.cronos.application.response.quote.InternalQuoteResponse;
import com.ninsky.cronos.application.service.quote.QuoteService;
import com.ninsky.cronos.domain.entity.enums.QuoteStatus;
import com.ninsky.cronos.quote.api.AcceptQuoteRequest;
import com.ninsky.cronos.quote.api.AcceptQuoteResponse;
import com.ninsky.cronos.quote.api.QuoteStats;
import com.ninsky.cronos.quote.api.RejectQuoteRequest;
import com.ninsky.cronos.quote.api.ShareQuoteRequest;
import com.ninsky.cronos.quote.application.QuoteLifecycleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@PreAuthorize(Authorities.QUOTE_READ)
@RequestMapping("/quotes")
@RequiredArgsConstructor @Slf4j
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Quotes", description = "Private endpoints for bakers to manage their quotes")
public class QuoteController {

    private final QuoteService quoteService;
    private final QuoteLifecycleService lifecycle;

    @GetMapping
    @Operation(summary = "Get paginated quotes",
            description = "Quotes of the authenticated baker. search matches number, client name, phone or email (min 2 chars, "
                    + "accent-insensitive); statuses filters (repeatable); sort whitelist: createdAt, quoteNumber, clientName, total, "
                    + "eventDate, validUntil (default createdAt,desc).")
    public ResponseEntity<ApiResponse<Page<InternalQuoteResponse>>> getQuotes(
            Authentication authentication, @RequestParam(required = false) String search,
            @RequestParam(required = false) List<QuoteStatus> statuses, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) String sort) {
        Page<InternalQuoteResponse> response = quoteService.page(authentication.getName(), search, statuses, page, size, sort);
        return ResponseEntity.ok(ApiResponse.success("Quotes retrieved successfully", response));
    }

    @GetMapping("/stats")
    @Operation(summary = "Quote figures", description = "Counts per status, open value, accepted value (30 days), conversion (90 days) and quotes expiring within 3 days")
    public ResponseEntity<ApiResponse<QuoteStats>> stats() {
        return ResponseEntity.ok(ApiResponse.success(null, lifecycle.stats()));
    }

    @PostMapping
    @PreAuthorize(Authorities.QUOTE_CREATE)
    @Operation(summary = "Create a new quote", description = "Generates a financial quote and a public sharing token")
    public ResponseEntity<ApiResponse<InternalQuoteResponse>> createQuote(Authentication authentication, @Valid @RequestBody CreateQuoteRequest request) {
        Warned<InternalQuoteResponse> response = quoteService.createQuote(authentication.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Quote generated successfully", response.data(), response.warnings()));
    }

    @GetMapping("/{quoteId}")
    @Operation(summary = "Get quote by ID", description = "Retrieves the full details of a specific quote for editing")
    public ResponseEntity<ApiResponse<InternalQuoteResponse>> getQuote(Authentication authentication, @PathVariable UUID quoteId) {
        InternalQuoteResponse response = quoteService.getQuoteById(authentication.getName(), quoteId);
        return ResponseEntity.ok(ApiResponse.success("Quote retrieved successfully", response));
    }

    @PutMapping("/{quoteId}")
    @PreAuthorize(Authorities.QUOTE_UPDATE)
    @Operation(summary = "Update a draft quote", description = "Modifies an existing quote if it has not been finalized")
    public ResponseEntity<ApiResponse<InternalQuoteResponse>> updateQuote(Authentication authentication, @PathVariable UUID quoteId, @Valid @RequestBody CreateQuoteRequest request) {
        Warned<InternalQuoteResponse> response = quoteService.updateQuote(authentication.getName(), quoteId, request);
        return ResponseEntity.ok(ApiResponse.success("Quote updated successfully", response.data(), response.warnings()));
    }

    @PostMapping("/{quoteId}/revoke")
    @PreAuthorize(Authorities.QUOTE_UPDATE)
    @Operation(summary = "Revoke public access", description = "Instantly disables the public link for a quote")
    public ResponseEntity<ApiResponse<Void>> revokeQuote(Authentication authentication, @PathVariable UUID quoteId) {
        lifecycle.revoke(quoteId);
        return ResponseEntity.ok(ApiResponse.success("Public link revoked successfully", null));
    }

    @PostMapping("/{quoteId}/send-email")
    @PreAuthorize(Authorities.QUOTE_SHARE)
    @Operation(summary = "Send quote via email", description = "Sends the public link of the quote to the client's email")
    public ResponseEntity<ApiResponse<Void>> sendQuoteEmail(Authentication authentication, @PathVariable UUID quoteId) {
        lifecycle.sendByEmail(quoteId);
        return ResponseEntity.accepted().body(ApiResponse.success("Quote sent via email successfully", null));
    }

    @PostMapping("/{quoteId}/shared")
    @PreAuthorize(Authorities.QUOTE_SHARE)
    @Operation(summary = "Record an out-of-band share", description = "WhatsApp, copied link or own email: DRAFT/EXPIRED move to SENT")
    public ResponseEntity<Void> shared(@PathVariable UUID quoteId, @Valid @RequestBody ShareQuoteRequest request) {
        lifecycle.share(quoteId, request.channel());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{quoteId}/accept")
    @PreAuthorize(Authorities.QUOTE_UPDATE)
    @Operation(summary = "Accept a quote", description = "Optionally schedules the order in the calendar in the same transaction")
    public ResponseEntity<ApiResponse<AcceptQuoteResponse>> accept(@PathVariable UUID quoteId, @Valid @RequestBody AcceptQuoteRequest request) {
        return ResponseEntity.ok(ApiResponse.success(null, lifecycle.accept(quoteId, request)));
    }

    @PostMapping("/{quoteId}/reject")
    @PreAuthorize(Authorities.QUOTE_UPDATE)
    @Operation(summary = "Reject a quote", description = "OTHER requires a comment")
    public ResponseEntity<Void> reject(@PathVariable UUID quoteId, @Valid @RequestBody RejectQuoteRequest request) {
        lifecycle.reject(quoteId, request.reason(), request.comment());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{quoteId}/duplicate")
    @PreAuthorize(Authorities.QUOTE_CREATE)
    @Operation(summary = "Duplicate a quote", description = "New DRAFT with the same client, items and fees")
    public ResponseEntity<ApiResponse<InternalQuoteResponse>> duplicate(@PathVariable UUID quoteId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Quote duplicated successfully", quoteService.toSummary(lifecycle.duplicate(quoteId))));
    }

    @DeleteMapping("/{quoteId}")
    @PreAuthorize(Authorities.QUOTE_DELETE)
    @Operation(summary = "Delete a draft", description = "Only DRAFT quotes can be deleted")
    public ResponseEntity<Void> delete(@PathVariable UUID quoteId) {
        lifecycle.delete(quoteId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{quoteId}/details")
    @Operation(summary = "Get analytical quote details", description = "Retrieves quote details including profit margins and access logs for the baker dashboard")
    public ResponseEntity<ApiResponse<BakerQuoteDetailResponse>> getQuoteDetails(Authentication authentication, @PathVariable UUID quoteId) {

        BakerQuoteDetailResponse response = quoteService.getQuoteDetailsForBaker(authentication.getName(), quoteId);
        return ResponseEntity.ok(ApiResponse.success("Quote details retrieved successfully", response));
    }
}
