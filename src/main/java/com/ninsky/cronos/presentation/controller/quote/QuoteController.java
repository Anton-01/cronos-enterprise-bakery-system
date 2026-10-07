package com.ninsky.cronos.presentation.controller.quote;

import org.springframework.security.access.prepost.PreAuthorize;
import com.ninsky.cronos.iam.permission.Authorities;
import com.ninsky.cronos.application.request.quote.CreateQuoteRequest;
import com.ninsky.cronos.application.response.core.ApiResponse;
import com.ninsky.cronos.kitchen.shared.Warned;
import com.ninsky.cronos.application.response.quote.BakerQuoteDetailResponse;
import com.ninsky.cronos.application.response.quote.InternalQuoteResponse;
import com.ninsky.cronos.application.service.quote.QuoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@PreAuthorize(Authorities.QUOTE_READ)
@RequestMapping("/quotes")
@RequiredArgsConstructor @Slf4j
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Quotes", description = "Private endpoints for bakers to manage their quotes")
public class QuoteController {

    private final QuoteService quoteService;

    @GetMapping
    @Operation(summary = "Get paginated quotes", description = "Retrieves a list of quotes belonging to the authenticated baker")
    public ResponseEntity<ApiResponse<Page<InternalQuoteResponse>>> getQuotes(Authentication authentication, Pageable pageable) {
        log.info("User {} requesting quotes list with pageable: {}", authentication.getName(), pageable);
        Page<InternalQuoteResponse> response = quoteService.getQuotesByUser(authentication.getName(), pageable);
        return ResponseEntity.ok(ApiResponse.success("Quotes retrieved successfully", response));
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
        quoteService.revokeQuoteLink(authentication.getName(), quoteId);
        return ResponseEntity.ok(ApiResponse.success("Public link revoked successfully", null));
    }

    @PostMapping("/{quoteId}/send-email")
    @PreAuthorize(Authorities.QUOTE_SHARE)
    @Operation(summary = "Send quote via email", description = "Sends the public link of the quote to the client's email")
    public ResponseEntity<ApiResponse<Void>> sendQuoteEmail(Authentication authentication, @PathVariable UUID quoteId) {
        quoteService.sendQuoteByEmail(authentication.getName(), quoteId);
        return ResponseEntity.ok(ApiResponse.success("Quote sent via email successfully", null));
    }

    @GetMapping("/{quoteId}/details")
    @Operation(summary = "Get analytical quote details", description = "Retrieves quote details including profit margins and access logs for the baker dashboard")
    public ResponseEntity<ApiResponse<BakerQuoteDetailResponse>> getQuoteDetails(Authentication authentication, @PathVariable UUID quoteId) {

        BakerQuoteDetailResponse response = quoteService.getQuoteDetailsForBaker(authentication.getName(), quoteId);
        return ResponseEntity.ok(ApiResponse.success("Quote details retrieved successfully", response));
    }
}
