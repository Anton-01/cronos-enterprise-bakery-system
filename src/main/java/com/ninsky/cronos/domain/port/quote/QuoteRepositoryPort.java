package com.ninsky.cronos.domain.port.quote;

import com.ninsky.cronos.domain.model.quote.Quote;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuoteRepositoryPort {
    Quote save(Quote quote);
    Optional<Quote> findById(UUID id);
    Optional<Quote> findByPublicToken(String token);
    Optional<Quote> findByIdAndUserId(UUID quoteId, UUID userId);
    Page<Quote> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    /** The quotes of {@code ids}, in no particular order. */
    List<Quote> findAllById(Collection<UUID> ids);
}
