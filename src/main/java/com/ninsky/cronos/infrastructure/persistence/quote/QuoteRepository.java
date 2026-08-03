package com.ninsky.cronos.infrastructure.persistence.quote;

import com.ninsky.cronos.domain.entity.quote.Quote;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface QuoteRepository extends JpaRepository<Quote, UUID> {
    Optional<Quote> findByPublicToken(String token);
    Optional<Quote> findByIdAndUserId(UUID quoteId, UUID id);
    Page<Quote> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
