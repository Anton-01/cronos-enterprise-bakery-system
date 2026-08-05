package com.ninsky.cronos.domain.port.quote;

import com.ninsky.cronos.domain.model.quote.Quote;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface QuoteRepositoryPort {
    Quote save(Quote quote);
    Optional<Quote> findById(UUID id);
    Optional<Quote> findByPublicToken(String token);
    Optional<Quote> findByIdAndUserId(UUID quoteId, UUID userId);
    Page<Quote> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
