package com.ninsky.cronos.infrastructure.persistence.core.adapter;

import com.ninsky.cronos.domain.entity.enums.RecordStatus;
import com.ninsky.cronos.domain.entity.enums.UnitDimension;
import com.ninsky.cronos.domain.model.core.UnitType;
import com.ninsky.cronos.domain.port.core.UnitTypeSearchCriteria;
import com.ninsky.cronos.infrastructure.persistence.jdbc.JdbcPages;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Read side of {@code unit_types} in plain SQL (page and total in one statement, soft-deleted rows excluded). */
@Repository
public class UnitTypeCustomRepository {

    private static final String COLUMNS = "u.id, u.code_identity, u.name, u.dimension, u.status, u.created_at, u.created_by, "
            + "u.updated_at, u.updated_by";
    private static final String FROM = " FROM unit_types u WHERE u.deleted_at IS NULL";
    private static final Map<String, String> SORTABLE = Map.of(
            "id", "u.id", "codeIdentity", "u.code_identity", "name", "u.name", "dimension", "u.dimension", "status", "u.status",
            "createdAt", "u.created_at", "updatedAt", "u.updated_at");

    private static final RowMapper<UnitType> ROW = (rs, row) -> UnitType.builder()
            .id(rs.getLong("id"))
            .codeIdentity(rs.getString("code_identity"))
            .name(rs.getString("name"))
            .dimension(UnitDimension.valueOf(rs.getString("dimension")))
            .status(RecordStatus.valueOf(rs.getString("status")))
            .createdAt(local(rs.getTimestamp("created_at")))
            .createdBy(rs.getString("created_by"))
            .updatedAt(local(rs.getTimestamp("updated_at")))
            .updatedBy(rs.getString("updated_by"))
            .build();

    private final NamedParameterJdbcTemplate jdbc;

    public UnitTypeCustomRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UnitType> findById(Long id) {
        return jdbc.query("SELECT " + COLUMNS + FROM + " AND u.id = :id", new MapSqlParameterSource("id", id), ROW).stream().findFirst();
    }

    /** Active unit types by name (the selectable catalog). */
    public List<UnitType> findAllActive() {
        return jdbc.query("SELECT " + COLUMNS + FROM + " AND u.status = 'ACTIVE' ORDER BY u.name ASC", new MapSqlParameterSource(), ROW);
    }

    public Page<UnitType> search(UnitTypeSearchCriteria criteria, Pageable pageable) {
        List<String> filters = new ArrayList<>();
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (criteria.search() != null) {
            filters.add("(lower(u.code_identity) LIKE :pattern ESCAPE '\\' OR lower(u.name) LIKE :pattern ESCAPE '\\')");
            params.addValue("pattern", "%" + UnitTypeRepositoryAdapter.escapeLike(criteria.search().toLowerCase(Locale.ROOT)) + "%");
        }
        if (criteria.dimension() != null) {
            filters.add("u.dimension = :dimension");
            params.addValue("dimension", criteria.dimension().name());
        }
        if (criteria.status() != null) {
            filters.add("u.status = :status");
            params.addValue("status", criteria.status().name());
        }
        String where = FROM + filters.stream().map(filter -> " AND " + filter).reduce("", String::concat);
        String sql = "SELECT " + COLUMNS + ", " + JdbcPages.TOTAL_COLUMN + where + JdbcPages.orderBy(pageable.getSort(), SORTABLE, "u.id");
        return JdbcPages.page(jdbc, sql, params, pageable, ROW, "SELECT count(*)" + where);
    }

    private static LocalDateTime local(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
