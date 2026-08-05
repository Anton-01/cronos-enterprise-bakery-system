package com.ninsky.cronos.infrastructure.persistence.quote.adapter;

import com.ninsky.cronos.domain.model.quote.QuoteAccessLog;
import com.ninsky.cronos.domain.port.quote.QuoteAccessLogRepositoryPort;
import com.ninsky.cronos.infrastructure.persistence.quote.QuoteAccessLogJpaRepository;
import com.ninsky.cronos.infrastructure.persistence.quote.mapper.QuoteAccessLogMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class QuoteAccessLogRepositoryAdapter implements QuoteAccessLogRepositoryPort {

    private final QuoteAccessLogJpaRepository jpaRepository;
    private final QuoteAccessLogMapper mapper;

    public QuoteAccessLogRepositoryAdapter(QuoteAccessLogJpaRepository jpaRepository, QuoteAccessLogMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public QuoteAccessLog save(QuoteAccessLog log) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(log)));
    }

    @Override
    public List<QuoteAccessLog> findByQuoteIdOrderByAccessedAtDesc(UUID quoteId) {
        return jpaRepository.findByQuoteIdOrderByAccessedAtDesc(quoteId).stream().map(mapper::toDomain).collect(Collectors.toList());
    }
}
