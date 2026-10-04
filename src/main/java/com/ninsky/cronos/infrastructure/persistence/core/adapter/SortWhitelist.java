package com.ninsky.cronos.infrastructure.persistence.core.adapter;

import com.ninsky.cronos.infrastructure.exception.ValidationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Map;

/**
 * Translates client-facing sort keys (the response's JSON field names) into entity paths and
 * rejects anything else with a 400 — instead of letting Spring Data throw
 * {@code PropertyReferenceException} (a 500) or sort by an unintended column.
 */
final class SortWhitelist {

    private final Map<String, String> clientToEntityPath;

    SortWhitelist(Map<String, String> clientToEntityPath) {
        this.clientToEntityPath = Map.copyOf(clientToEntityPath);
    }

    Pageable translate(Pageable pageable) {
        if (pageable.getSort().isUnsorted()) {
            return pageable;
        }
        Sort translated = Sort.by(pageable.getSort().stream()
                .map(order -> order.withProperty(entityPathOf(order.getProperty())))
                .toList());
        return pageable.isPaged()
                ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), translated)
                : Pageable.unpaged(translated);
    }

    private String entityPathOf(String clientProperty) {
        String path = clientToEntityPath.get(clientProperty);
        if (path == null) {
            throw new ValidationException("Unsupported sort property '" + clientProperty + "'. Allowed: "
                    + clientToEntityPath.keySet().stream().sorted().toList());
        }
        return path;
    }
}
