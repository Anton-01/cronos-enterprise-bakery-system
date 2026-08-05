package com.ninsky.cronos.infrastructure.persistence.quote.mapper;

import com.ninsky.cronos.domain.model.quote.QuoteAccessLog;
import com.ninsky.cronos.infrastructure.persistence.quote.entity.QuoteAccessLogJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class QuoteAccessLogMapper {

    public QuoteAccessLog toDomain(QuoteAccessLogJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return QuoteAccessLog.builder()
                .id(entity.getId())
                .quoteId(entity.getQuoteId())
                .ipAddress(entity.getIpAddress())
                .userAgent(entity.getUserAgent())
                .accessedAt(entity.getAccessedAt())
                .build();
    }

    public QuoteAccessLogJpaEntity toEntity(QuoteAccessLog domain) {
        if (domain == null) {
            return null;
        }
        return QuoteAccessLogJpaEntity.builder()
                .id(domain.getId())
                .quoteId(domain.getQuoteId())
                .ipAddress(domain.getIpAddress())
                .userAgent(domain.getUserAgent())
                .accessedAt(domain.getAccessedAt())
                .build();
    }
}
