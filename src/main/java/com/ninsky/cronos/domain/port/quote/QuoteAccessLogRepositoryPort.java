package com.ninsky.cronos.domain.port.quote;

import com.ninsky.cronos.domain.model.quote.QuoteAccessLog;

import java.util.List;
import java.util.UUID;

public interface QuoteAccessLogRepositoryPort {
    QuoteAccessLog save(QuoteAccessLog log);
    List<QuoteAccessLog> findByQuoteIdOrderByAccessedAtDesc(UUID quoteId);
}
