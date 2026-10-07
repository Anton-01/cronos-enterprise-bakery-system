package com.ninsky.cronos.application.response.quote;

import com.ninsky.cronos.domain.entity.enums.QuoteStatus;
import com.ninsky.cronos.finance.pricing.FinanceRoundingMode;
import com.ninsky.cronos.finance.pricing.TaxFactorType;
import lombok.Builder;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record InternalQuoteResponse(
        UUID id,
        String quoteNumber,
        String clientName,
        String clientEmail,
        String clientPhone,
        String clientAddress,
        String notes,
        BigDecimal total,
        BigDecimal taxRate,
        String currency,
        QuoteStatus status,
        int validDays,
        List<InternalQuoteItemResponse> items,
        LocalDateTime createdAt,
        String publicToken,
        BigDecimal deliveryFee,
        BigDecimal extraFee,
        String extraFeeDescription,
        // Pricing snapshot (spec §11.4)
        Long taxRateId,
        TaxFactorType taxFactorType,
        Integer currencyDecimalPlaces,
        Boolean pricesIncludeTax,
        FinanceRoundingMode roundingMode,
        BigDecimal subtotal,
        BigDecimal taxAmount,
        // An ingredient price changed since the items were priced (kitchen §4.5); saving re-prices
        boolean priceReviewRequired
) {}
