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

        // Optional: omitted → tenant default tax rate (spec §11.4)
        @DecimalMin(value = "0.0", message = "Tax rate cannot be negative")
        @DecimalMax(value = "100.0", message = "Tax rate cannot exceed 100")
        @Digits(integer = 3, fraction = 4, message = "Tax rate allows at most 4 decimals")
        BigDecimal taxRate,

        // Optional: omitted → tenant default currency; must be an ACTIVE catalog code
        @Size(min = 3, max = 3)
        String currency,

        @Min(value = 1, message = "Valid days must be at least 1")
        int validDays,

        @NotEmpty(message = "A quote must have at least one item")
        @Valid
        List<QuoteItemRequest> items,

        @DecimalMin(value = "0.0", message = "Delivery fee cannot be negative")
        BigDecimal deliveryFee,

        @DecimalMin(value = "0.0", message = "Extra fee cannot be negative")
        BigDecimal extraFee,

        String extraFeeDescription,

        // Optional catalog preset; wins over taxRate (which must then match it)
        Long taxRateId
) {}
