package com.ninsky.cronos.infrastructure.persistence.catalog;

import com.ninsky.cronos.domain.port.ErrorCatalogEntry;
import com.ninsky.cronos.domain.port.ErrorCatalogPort;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Infrastructure adapter for {@link ErrorCatalogPort}. Sits on the exception hot path, so it
 * fetches only the columns it needs via JdbcTemplate (no JPA entity graph) and is cached —
 * catalog rows change rarely and are read on every error response.
 */
@Repository
public class JdbcErrorCatalogAdapter implements ErrorCatalogPort {

    private static final String SELECT_BY_CODE = """
            SELECT cer.error_code, cs.category, cs.http_status, cer.image_url,
                   cer.title_en, cer.title_es, cer.description_en, cer.description_es
            FROM custom_error_responses cer
            JOIN catalog_statuses cs ON cs.id = cer.status_id
            WHERE cer.error_code = ? AND cer.is_active = TRUE
            """;

    private static final RowMapper<ErrorCatalogEntry> ROW_MAPPER = (rs, rowNum) -> new ErrorCatalogEntry(
            rs.getString("error_code"),
            rs.getString("category"),
            rs.getInt("http_status"),
            rs.getString("image_url"),
            rs.getString("title_en"),
            rs.getString("title_es"),
            rs.getString("description_en"),
            rs.getString("description_es")
    );

    private final JdbcTemplate jdbcTemplate;

    public JdbcErrorCatalogAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Cacheable(value = "errorCatalog", key = "#errorCode")
    public Optional<ErrorCatalogEntry> findByCode(String errorCode) {
        return jdbcTemplate.query(SELECT_BY_CODE, ROW_MAPPER, errorCode).stream().findFirst();
    }
}
