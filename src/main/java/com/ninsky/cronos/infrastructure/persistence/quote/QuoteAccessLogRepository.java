package com.ninsky.cronos.infrastructure.persistence.quote;

import com.ninsky.cronos.domain.entity.quote.QuoteAccessLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface QuoteAccessLogRepository extends JpaRepository<QuoteAccessLog, UUID> {
    List<QuoteAccessLog> findByQuoteIdOrderByAccessedAtDesc(UUID quoteId);
}
