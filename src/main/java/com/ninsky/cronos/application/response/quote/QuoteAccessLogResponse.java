package com.ninsky.cronos.application.response.quote;

import lombok.Builder;
import java.time.LocalDateTime;

@Builder
public record QuoteAccessLogResponse(
        String ipAddress,
        String browserInfo, // Simplificaremos el User-Agent para que sea legible
        LocalDateTime accessedAt
) {}
