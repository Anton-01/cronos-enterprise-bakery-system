package com.ninsky.cronos.presentation.controller.quote;

import com.ninsky.cronos.application.response.core.ApiResponse;
import com.ninsky.cronos.application.response.quote.PublicQuoteResponse;
import com.ninsky.cronos.application.service.quote.QuoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

        String ipAddress = request.getHeader("X-Forwarded-For");
        if (ipAddress == null || ipAddress.isEmpty()) {
            ipAddress = request.getRemoteAddr();
        }

        String userAgent = request.getHeader("User-Agent");
        PublicQuoteResponse response = quoteService.getPublicQuote(token, ipAddress, userAgent);
        return ResponseEntity.ok(ApiResponse.success("Quote retrieved successfully", response));
    }
}
