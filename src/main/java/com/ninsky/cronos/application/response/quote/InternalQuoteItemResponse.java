package com.ninsky.cronos.application.response.quote;

import lombok.Builder;
import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record InternalQuoteItemResponse(
        UUID id,
        UUID recipeId,
        String productName,
        String productDescription,
        String productSize,
        BigDecimal quantity,
        BigDecimal unitCost,
        BigDecimal profitPercentage,
        BigDecimal unitPrice,
        BigDecimal subtotal,
        String notes
) {}
