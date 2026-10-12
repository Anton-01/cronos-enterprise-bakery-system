package com.ninsky.cronos.infrastructure.persistence.jdbc;

import com.ninsky.cronos.infrastructure.web.paging.CatalogPage;
import com.ninsky.cronos.infrastructure.web.paging.PageQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Page and total in one statement (performance-latency P1-3): the query selects {@code count(*) OVER () AS total_count}
 * next to the row columns, and this helper appends {@code LIMIT/OFFSET}, maps the rows and reads the total from the
 * first one. A second {@code count(*)} round trip only happens for a page past the end (no row to read the total from).
 * The result is an ordinary {@link Page}, so response shapes ({@code content}, {@code totalElements}, …) do not change.
 */
public final class JdbcPages {

    /** Column alias every paged query must select: {@code count(*) OVER () AS total_count}. */
    public static final String TOTAL = "total_count";
    /** The window expression to put in the select list. */
    public static final String TOTAL_COLUMN = "count(*) OVER () AS " + TOTAL;

    private JdbcPages() {
    }

    /**
     * Runs {@code sql} (already filtered and ordered, selecting {@link #TOTAL_COLUMN}) for one page.
     *
     * @param countSql {@code SELECT count(*) …} with the same filter, used only when the page is past the end
     */
    public static <T> Page<T> page(NamedParameterJdbcTemplate jdbc, String sql, MapSqlParameterSource params, Pageable pageable,
                                   RowMapper<T> rows, String countSql) {
        long[] total = {-1};
        String query = sql;
        if (pageable.isPaged()) {
            query = sql + " LIMIT :pageLimit OFFSET :pageOffset";
            params.addValue("pageLimit", pageable.getPageSize()).addValue("pageOffset", pageable.getOffset());
        }
        List<T> content = jdbc.query(query, params, (rs, rowNum) -> {
            if (total[0] < 0) {
                total[0] = rs.getLong(TOTAL);
            }
            return rows.mapRow(rs, rowNum);
        });
        if (total[0] < 0) {
            boolean pastTheEnd = pageable.isPaged() && pageable.getOffset() > 0;
            Long counted = pastTheEnd && countSql != null ? jdbc.queryForObject(countSql, params, Long.class) : null;
            total[0] = counted == null ? content.size() : counted;
        }
        return new PageImpl<>(content, pageable, total[0]);
    }

    /** One page of rows and the total of the whole filtered set. */
    public record Rows<T>(List<T> rows, long total) {
    }

    /**
     * Runs {@code sql}, which selects {@link #TOTAL_COLUMN} and already ends with its own {@code LIMIT/OFFSET}, and returns
     * the rows with the total read from the first one. {@code countSql} (same filter and parameters) runs only for a page
     * past the end; pass {@code offset} so that case is recognised.
     */
    public static <T> Rows<T> rows(NamedParameterJdbcTemplate jdbc, String sql, MapSqlParameterSource params, long offset,
                                   RowMapper<T> rows, String countSql) {
        long[] total = {-1};
        List<T> content = jdbc.query(sql, params, (rs, rowNum) -> {
            if (total[0] < 0) {
                total[0] = rs.getLong(TOTAL);
            }
            return rows.mapRow(rs, rowNum);
        });
        if (total[0] < 0) {
            Long counted = offset > 0 && countSql != null ? jdbc.queryForObject(countSql, params, Long.class) : null;
            total[0] = counted == null ? 0 : counted;
        }
        return new Rows<>(content, total[0]);
    }

    /** {@link #rows} as a {@link CatalogPage} for {@code query}. */
    public static <T> CatalogPage<T> catalogPage(NamedParameterJdbcTemplate jdbc, String sql, MapSqlParameterSource params, PageQuery query,
                                                 RowMapper<T> rows, String countSql) {
        Rows<T> result = rows(jdbc, sql, params, query.offset(), rows, countSql);
        return CatalogPage.of(result.rows(), query, result.total());
    }

    /**
     * {@code ORDER BY} for the requested sort, restricted to {@code columns} (API property → SQL expression); unknown
     * properties are ignored. {@code tieBreaker} (e.g. {@code c.id}) keeps pages stable and is the default order.
     */
    public static String orderBy(Sort sort, Map<String, String> columns, String tieBreaker) {
        List<String> parts = new ArrayList<>();
        for (Sort.Order order : sort) {
            String column = columns.get(order.getProperty());
            if (column != null) {
                String direction = order.isAscending() ? "ASC" : "DESC";
                parts.add(order.isIgnoreCase() ? "lower(" + column + ") " + direction : column + " " + direction);
            }
        }
        if (parts.stream().noneMatch(part -> part.toLowerCase(Locale.ROOT).startsWith(tieBreaker.toLowerCase(Locale.ROOT) + " "))) {
            parts.add(tieBreaker + " ASC");
        }
        return " ORDER BY " + String.join(", ", parts);
    }
}
