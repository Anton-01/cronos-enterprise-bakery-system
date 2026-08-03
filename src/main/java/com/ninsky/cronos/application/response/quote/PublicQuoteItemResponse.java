package com.ninsky.cronos.application.response.quote;

import lombok.Builder;
import java.math.BigDecimal;

@Builder
public record PublicQuoteItemResponse(
        String productName,
        String productDescription,
        String productSize,
        String mainImageUrl,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal
) { }
