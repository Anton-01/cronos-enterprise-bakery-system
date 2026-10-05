package com.ninsky.cronos.infrastructure.web.paging;

import com.ninsky.cronos.infrastructure.exception.ApiException;

import java.util.Locale;
import java.util.Map;

/**
 * A validated page request: 0-based {@code page}, {@code size} clamped to [1, 100], and a sort
 * resolved through a per-endpoint whitelist of API field → SQL expression (spec §1.1).
 */
public record PageQuery(int page, int size, String sortField, boolean ascending, String orderBySql) {

    public static final int MAX_SIZE = 100;
    public static final int DEFAULT_SIZE = 10;

    /**
     * @param sort      raw {@code field,asc|desc} or null for the default
     * @param whitelist API field → SQL ORDER BY expression
     */
    public static PageQuery of(Integer page, Integer size, String sort, Map<String, String> whitelist, String defaultSort) {
        int safePage = page == null || page < 0 ? 0 : page;
        int safeSize = size == null ? DEFAULT_SIZE : Math.clamp(size, 1, MAX_SIZE);
        String effective = sort == null || sort.isBlank() ? defaultSort : sort.trim();
        String[] parts = effective.split(",");
        String field = parts[0].trim();
        String expression = whitelist.get(field);
        if (expression == null) {
            throw ApiException.invalid("sort", "api.validation.sortField", field);
        }
        String direction = parts.length < 2 ? "asc" : parts[1].trim().toLowerCase(Locale.ROOT);
        if (!"asc".equals(direction) && !"desc".equals(direction)) {
            throw ApiException.invalid("sort", "api.validation.sortDirection", direction);
        }
        boolean ascending = "asc".equals(direction);
        return new PageQuery(safePage, safeSize, field, ascending, expression + (ascending ? " ASC" : " DESC") + " NULLS LAST");
    }

    public long offset() {
        return (long) page * size;
    }
}
