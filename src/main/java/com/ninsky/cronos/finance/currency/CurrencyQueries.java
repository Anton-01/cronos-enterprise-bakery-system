package com.ninsky.cronos.finance.currency;

import com.ninsky.cronos.finance.shared.FinanceStatus;
import com.ninsky.cronos.finance.shared.SqlSupport;
import com.ninsky.cronos.finance.shared.UserRefMapper;
import com.ninsky.cronos.infrastructure.web.paging.CatalogPage;
import com.ninsky.cronos.infrastructure.web.paging.PageQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Read side of currencies: list/detail projections with {@code inUse} computed by EXISTS (spec §9.2). */
@Repository
@RequiredArgsConstructor
public class CurrencyQueries {

    /** API sort field → SQL (spec §9.3). */
    public static final Map<String, String> SORTS = Map.of("code", "c.code", "name", "lower(c.name)", "status", "c.status");
    public static final String DEFAULT_SORT = "code,asc";

    /** Every table storing a currency code; legacy rows may hold it in lower case. */
    static final String IN_USE = "(EXISTS (SELECT 1 FROM quotes q WHERE q.currency IN (c.code, lower(c.code)))"
            + " OR EXISTS (SELECT 1 FROM raw_materials rm WHERE rm.currency IN (c.code, lower(c.code))))";

    private static final String SELECT = "SELECT c.id, c.code, c.numeric_code, c.name, c.symbol, c.decimal_places, c.symbol_position, "
            + "c.is_default, c.status, c.created_at, c.updated_at, c.version, " + IN_USE + " AS in_use, "
            + UserRefMapper.columns("c.updated_by") + " FROM currencies c" + UserRefMapper.join("c.updated_by");
    private static final String FILTER = " WHERE (CAST(:search AS text) IS NULL OR c.code ILIKE :search OR c.name ILIKE :search)"
            + " AND (CAST(:status AS text) IS NULL OR c.status = :status)";

    private final NamedParameterJdbcTemplate jdbc;
    private final UserRefMapper userRefs;

    public CatalogPage<CurrencyResponse> page(String search, FinanceStatus status, PageQuery query) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("search", SqlSupport.likePattern(search))
                .addValue("status", status == null ? null : status.name())
                .addValue("limit", query.size())
                .addValue("offset", query.offset());
        Long total = jdbc.queryForObject("SELECT count(*) FROM currencies c" + FILTER, params, Long.class);
        List<CurrencyResponse> content = jdbc.query(SELECT + FILTER + " ORDER BY " + query.orderBySql() + ", c.id ASC LIMIT :limit OFFSET :offset",
                params, rowMapper());
        return CatalogPage.of(content, query, total == null ? 0 : total);
    }

    public Optional<CurrencyResponse> find(long id) {
        return jdbc.query(SELECT + " WHERE c.id = :id", Map.of("id", id), rowMapper()).stream().findFirst();
    }

    public boolean inUse(long id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT " + IN_USE + " FROM currencies c WHERE c.id = :id", Map.of("id", id), Boolean.class));
    }

    private RowMapper<CurrencyResponse> rowMapper() {
        return (rs, rowNum) -> new CurrencyResponse(
                rs.getLong("id"), rs.getString("code"), rs.getString("numeric_code"), rs.getString("name"), rs.getString("symbol"),
                rs.getInt("decimal_places"), SymbolPosition.valueOf(rs.getString("symbol_position")), rs.getBoolean("is_default"),
                rs.getBoolean("in_use"), FinanceStatus.valueOf(rs.getString("status")), SqlSupport.instant(rs, "created_at"),
                SqlSupport.instant(rs, "updated_at"), userRefs.map(rs), rs.getLong("version"));
    }
}
