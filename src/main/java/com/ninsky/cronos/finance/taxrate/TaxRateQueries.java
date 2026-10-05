package com.ninsky.cronos.finance.taxrate;

import com.ninsky.cronos.finance.pricing.TaxFactorType;
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

/** Read side of IVA rates; {@code inUse} = referenced by a quote snapshot. */
@Repository
@RequiredArgsConstructor
public class TaxRateQueries {

    /** API sort field → SQL (spec §10.3). */
    public static final Map<String, String> SORTS = Map.of("name", "lower(t.name)", "ratePercent", "t.rate_percent",
            "validFrom", "t.valid_from", "status", "t.status");
    public static final String DEFAULT_SORT = "ratePercent,desc";

    static final String IN_USE = "EXISTS (SELECT 1 FROM quotes q WHERE q.tax_rate_id = t.id)";

    private static final String SELECT = "SELECT t.id, t.code, t.name, t.description, t.sat_tax_code, t.factor_type, t.rate_percent, "
            + "t.valid_from, t.valid_to, t.is_default, t.status, t.created_at, t.updated_at, t.version, " + IN_USE + " AS in_use, "
            + UserRefMapper.columns("t.updated_by") + " FROM tax_rates t" + UserRefMapper.join("t.updated_by");
    private static final String FILTER = " WHERE (CAST(:search AS text) IS NULL OR t.code ILIKE :search OR t.name ILIKE :search"
            + " OR t.description ILIKE :search) AND (CAST(:status AS text) IS NULL OR t.status = :status)";

    private final NamedParameterJdbcTemplate jdbc;
    private final UserRefMapper userRefs;

    public CatalogPage<TaxRateResponse> page(String search, FinanceStatus status, PageQuery query) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("search", SqlSupport.likePattern(search))
                .addValue("status", status == null ? null : status.name())
                .addValue("limit", query.size())
                .addValue("offset", query.offset());
        Long total = jdbc.queryForObject("SELECT count(*) FROM tax_rates t" + FILTER, params, Long.class);
        List<TaxRateResponse> content = jdbc.query(SELECT + FILTER + " ORDER BY " + query.orderBySql() + ", t.id ASC LIMIT :limit OFFSET :offset",
                params, rowMapper());
        return CatalogPage.of(content, query, total == null ? 0 : total);
    }

    public Optional<TaxRateResponse> find(long id) {
        return jdbc.query(SELECT + " WHERE t.id = :id", Map.of("id", id), rowMapper()).stream().findFirst();
    }

    public boolean inUse(long id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT " + IN_USE + " FROM tax_rates t WHERE t.id = :id", Map.of("id", id), Boolean.class));
    }

    private RowMapper<TaxRateResponse> rowMapper() {
        return (rs, rowNum) -> new TaxRateResponse(
                rs.getLong("id"), rs.getString("code"), rs.getString("name"), rs.getString("description"), rs.getString("sat_tax_code"),
                TaxFactorType.valueOf(rs.getString("factor_type")), rs.getBigDecimal("rate_percent"), SqlSupport.date(rs, "valid_from"),
                SqlSupport.date(rs, "valid_to"), rs.getBoolean("is_default"), rs.getBoolean("in_use"),
                FinanceStatus.valueOf(rs.getString("status")), SqlSupport.instant(rs, "created_at"), SqlSupport.instant(rs, "updated_at"),
                userRefs.map(rs), rs.getLong("version"));
    }
}
