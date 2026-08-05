package com.ninsky.cronos.infrastructure.persistence.quote;

import com.ninsky.cronos.infrastructure.persistence.quote.entity.QuoteJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface QuoteJpaRepository extends JpaRepository<QuoteJpaEntity, UUID> {
    Optional<QuoteJpaEntity> findByPublicToken(String token);
    Optional<QuoteJpaEntity> findByIdAndUserId(UUID quoteId, UUID id);
    Page<QuoteJpaEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
