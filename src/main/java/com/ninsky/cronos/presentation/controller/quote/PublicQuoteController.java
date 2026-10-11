package com.ninsky.cronos.presentation.controller.quote;

import com.ninsky.cronos.application.response.core.ApiResponse;
import com.ninsky.cronos.application.response.quote.PublicQuoteResponse;
import com.ninsky.cronos.application.service.quote.QuoteService;
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

    @GetMapping("/{token}")
    @Operation(summary = "View public quote", description = "Retrieves quote details and tracks client views")
    public ResponseEntity<ApiResponse<PublicQuoteResponse>> viewPublicQuote(@PathVariable String token, HttpServletRequest request) {
        PublicQuoteResponse response = quoteService.getPublicQuote(token, clientIp(request), request.getHeader("User-Agent"));
        return ResponseEntity.ok(ApiResponse.success("Quote retrieved successfully", response));
    }

    @PostMapping("/{token}/accept")
    @Operation(summary = "Accept the quote", description = "The client accepts while the quote is SENT/VIEWED and still valid")
    public ResponseEntity<ApiResponse<PublicQuoteResponse>> accept(@PathVariable String token, @Valid @RequestBody PublicAcceptRequest body,
                                                                   HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(null, quoteService.acceptPublic(token, clientIp(request), body)));
    }

    @PostMapping("/{token}/reject")
    @Operation(summary = "Reject the quote", description = "OTHER requires a comment")
    public ResponseEntity<ApiResponse<PublicQuoteResponse>> reject(@PathVariable String token, @Valid @RequestBody PublicRejectRequest body,
                                                                   HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(null, quoteService.rejectPublic(token, clientIp(request), body)));
    }

    /** The first X-Forwarded-For hop (set by our proxy), else the socket address. */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return request.getRemoteAddr();
        }
        int comma = forwarded.indexOf(',');
        return (comma < 0 ? forwarded : forwarded.substring(0, comma)).trim();
    }
}
