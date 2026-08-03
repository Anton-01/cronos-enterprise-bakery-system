package com.ninsky.cronos.application.request.quote;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record QuoteItemRequest(
        UUID recipeId,

        @NotBlank(message = "Product name is required")
        String productName,

        String productDescription,
        String productSize,

        @NotNull
        @DecimalMin(value = "0.01", message = "Quantity must be greater than zero")
        BigDecimal quantity,

        @NotNull
        @DecimalMin(value = "0.00", message = "Unit cost cannot be negative")
        BigDecimal unitCost,

        @NotNull
        BigDecimal profitPercentage,

        @NotNull
        @DecimalMin(value = "0.01", message = "Unit price must be greater than zero")
        BigDecimal unitPrice,

        String notes
) {}
