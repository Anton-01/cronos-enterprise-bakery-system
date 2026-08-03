package com.ninsky.cronos.application.response.quote;

import lombok.Builder;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record BakerQuoteDetailResponse(
        UUID id,
        String quoteNumber,
        String clientName,
        String clientEmail,
        String clientPhone,
        String status,
        LocalDateTime createdAt,
        LocalDateTime validUntil,

        // Matemáticas de Negocio
        BigDecimal subtotal,
        BigDecimal taxAmount,
        BigDecimal deliveryFee,
        BigDecimal extraFee,
        BigDecimal totalRevenue, // Lo que paga el cliente
        BigDecimal totalProductCost, // Lo que te cuesta hacerlo
        BigDecimal estimatedProfit, // Tu ganancia limpia

        Integer viewsCount,
        boolean isRevoked,
        String publicToken,

        List<InternalQuoteItemResponse> items,
        List<QuoteAccessLogResponse> accessLogs
) {}
