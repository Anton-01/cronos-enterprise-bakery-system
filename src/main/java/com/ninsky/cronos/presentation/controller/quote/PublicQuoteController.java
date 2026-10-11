package com.ninsky.cronos.presentation.controller.quote;

import com.ninsky.cronos.application.response.core.ApiResponse;
import com.ninsky.cronos.application.response.quote.PublicQuoteResponse;
import com.ninsky.cronos.application.service.quote.QuoteService;
import com.ninsky.cronos.infrastructure.util.auth.RequestContextUtil;
import io.swagger.v3.oas.annotations.Operation;
import com.ninsky.cronos.quote.api.PublicAcceptRequest;
import com.ninsky.cronos.quote.api.PublicRejectRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/public/quotes")
@RequiredArgsConstructor
@Tag(name = "Public Quotes", description = "Public access to quotes for clients via secure tokens")
public class PublicQuoteController {

    private final QuoteService quoteService;
    private final RequestContextUtil requestContext;

    @GetMapping("/{token}")
    @Operation(summary = "View public quote", description = "Retrieves quote details and tracks client views")
    public ResponseEntity<ApiResponse<PublicQuoteResponse>> viewPublicQuote(@PathVariable String token, HttpServletRequest request) {
        PublicQuoteResponse response = quoteService.getPublicQuote(token, requestContext.getClientIp(), request.getHeader("User-Agent"));
        return ResponseEntity.ok(ApiResponse.success("Quote retrieved successfully", response));
    }

    @PostMapping("/{token}/accept")
    @Operation(summary = "Accept the quote", description = "The client accepts while the quote is SENT/VIEWED and still valid")
    public ResponseEntity<ApiResponse<PublicQuoteResponse>> accept(@PathVariable String token, @Valid @RequestBody PublicAcceptRequest body) {
        return ResponseEntity.ok(ApiResponse.success(null, quoteService.acceptPublic(token, requestContext.getClientIp(), body)));
    }

    @PostMapping("/{token}/reject")
    @Operation(summary = "Reject the quote", description = "OTHER requires a comment")
    public ResponseEntity<ApiResponse<PublicQuoteResponse>> reject(@PathVariable String token, @Valid @RequestBody PublicRejectRequest body) {
        return ResponseEntity.ok(ApiResponse.success(null, quoteService.rejectPublic(token, requestContext.getClientIp(), body)));
    }
}
