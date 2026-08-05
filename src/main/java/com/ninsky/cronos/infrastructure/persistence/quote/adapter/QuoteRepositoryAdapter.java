package com.ninsky.cronos.infrastructure.persistence.quote.adapter;

import com.ninsky.cronos.domain.model.quote.Quote;
import com.ninsky.cronos.domain.port.quote.QuoteRepositoryPort;
import com.ninsky.cronos.infrastructure.persistence.quote.QuoteJpaRepository;
import com.ninsky.cronos.infrastructure.persistence.quote.mapper.QuoteMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class QuoteRepositoryAdapter implements QuoteRepositoryPort {

    private final QuoteJpaRepository jpaRepository;
    private final QuoteMapper mapper;

    public QuoteRepositoryAdapter(QuoteJpaRepository jpaRepository, QuoteMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Quote save(Quote quote) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(quote)));
    }

    @Override
    public Optional<Quote> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Quote> findByPublicToken(String token) {
        return jpaRepository.findByPublicToken(token).map(mapper::toDomain);
    }

    @Override
    public Optional<Quote> findByIdAndUserId(UUID quoteId, UUID userId) {
        return jpaRepository.findByIdAndUserId(quoteId, userId).map(mapper::toDomain);
    }

    @Override
    public Page<Quote> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable) {
        return jpaRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable).map(mapper::toDomain);
    }
}
