package com.ninsky.cronos.infrastructure.persistence.quote;

import com.ninsky.cronos.infrastructure.persistence.quote.entity.QuoteAccessLogJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface QuoteAccessLogJpaRepository extends JpaRepository<QuoteAccessLogJpaEntity, UUID> {
    List<QuoteAccessLogJpaEntity> findByQuoteIdOrderByAccessedAtDesc(UUID quoteId);
}
