package com.ninsky.cronos.kitchen.legacy;

import com.ninsky.cronos.infrastructure.web.paging.CatalogPage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.function.Function;

/** Spring {@link Page}s for the deprecated endpoints, which predate {@link CatalogPage}. */
final class LegacyPages {

    private LegacyPages() {
    }

    static <T, R> Page<R> of(List<T> all, Pageable pageable, Function<T, R> mapper) {
        if (pageable.isUnpaged()) {
            return new PageImpl<>(all.stream().map(mapper).toList());
        }
        int from = (int) Math.min(pageable.getOffset(), all.size());
        int to = Math.min(from + pageable.getPageSize(), all.size());
        return new PageImpl<>(all.subList(from, to).stream().map(mapper).toList(), pageable, all.size());
    }

    static <T, R> Page<R> of(CatalogPage<T> page, Function<T, R> mapper) {
        return new PageImpl<>(page.content().stream().map(mapper).toList(), PageRequest.of(page.pageNumber(), page.pageSize()),
                page.totalElements());
    }
}
