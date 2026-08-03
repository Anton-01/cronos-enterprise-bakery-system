package com.ninsky.cronos.application.request.quote;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Builder;
import java.math.BigDecimal;
import java.util.List;

@Builder
public record CreateQuoteRequest(
        @NotBlank(message = "Client name is required")
        @Size(max = 200)
        String clientName,

        @Email(message = "Invalid email format")
        String clientEmail,

        String clientPhone,
        String clientAddress,
        String notes,

        @NotNull(message = "Tax rate is required")
        @DecimalMin(value = "0.0", message = "Tax rate cannot be negative")
        BigDecimal taxRate,

        @NotNull(message = "Currency is required")
        @Size(min = 3, max = 3)
        String currency,

        @Min(value = 1, message = "Valid days must be at least 1")
        int validDays,

        @NotEmpty(message = "A quote must have at least one item")
        @Valid
        List<QuoteItemRequest> items,

        BigDecimal deliveryFee,

        BigDecimal extraFee,

        String extraFeeDescription
) {}
