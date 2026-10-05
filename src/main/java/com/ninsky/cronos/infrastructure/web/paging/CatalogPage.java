package com.ninsky.cronos.infrastructure.web.paging;

import java.util.List;
import java.util.function.Function;

/** The paged-list shape of the IAM/Finance contract (spec §1.1); {@code pageNumber} is 0-based. */
public record CatalogPage<T>(List<T> content, int pageNumber, int pageSize, long totalElements, int totalPages, boolean last) {

    public CatalogPage {
        content = List.copyOf(content);
    }

    public static <T> CatalogPage<T> of(List<T> content, PageQuery query, long totalElements) {
        int totalPages = (int) Math.ceil(totalElements / (double) query.size());
        return new CatalogPage<>(content, query.page(), query.size(), totalElements, totalPages, query.page() >= totalPages - 1);
    }

    public <R> CatalogPage<R> map(Function<? super T, ? extends R> mapper) {
        return new CatalogPage<>(content.stream().<R>map(mapper).toList(), pageNumber, pageSize, totalElements, totalPages, last);
    }
}
