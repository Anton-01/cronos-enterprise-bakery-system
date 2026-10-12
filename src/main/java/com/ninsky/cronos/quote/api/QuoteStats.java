package com.ninsky.cronos.quote.api;

import com.ninsky.cronos.domain.entity.enums.QuoteStatus;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Quote dashboard figures (§4.5). Amounts are in {@code currency} (the tenant default): quotes in other currencies are
 * not converted (there are no exchange rates) and therefore not summed.
 *
 * @param conversionRate accepted ÷ (accepted + rejected + expired) over 90 days; null without decisions
 */
public record QuoteStats(Map<QuoteStatus, Long> byStatus, BigDecimal openValue, BigDecimal acceptedValue30d, Double conversionRate,
                         long expiringSoon, String currency) {
}
