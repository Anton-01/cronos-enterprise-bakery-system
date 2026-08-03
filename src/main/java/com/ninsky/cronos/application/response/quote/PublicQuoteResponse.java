package com.ninsky.cronos.application.response.quote;

import lombok.Builder;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record PublicQuoteResponse(
        String quoteNumber,
        String bakerName,
        String clientName,
        String notes,
        LocalDate quoteDate,
        LocalDate validUntil,
        BigDecimal subtotal,
        BigDecimal taxRate,
        BigDecimal taxAmount,
        BigDecimal total,
        String currency,
        String status,
        boolean isExpired,
        List<PublicQuoteItemResponse> items,
        BigDecimal deliveryFee,
        BigDecimal extraFee,
        String extraFeeDescription
) {}
