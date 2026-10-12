package com.ninsky.cronos.infrastructure.persistence.core.adapter;

import com.ninsky.cronos.domain.model.core.Category;
import com.ninsky.cronos.domain.entity.enums.CategoryScope;
import com.ninsky.cronos.domain.entity.enums.CategoryStatus;
import com.ninsky.cronos.domain.entity.enums.CategoryType;
import com.ninsky.cronos.infrastructure.persistence.jdbc.JdbcPages;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Read side of {@code categories} in plain SQL (performance-latency P1-1/P1-3): only the columns of {@link Category},
 * filtered by type in the query (never an unfiltered {@code findAll}), page and total in one statement. Trashed rows are
 * excluded like the JPA entity's {@code @SQLRestriction} did.
 */
@Repository
public class CategoryCustomRepository {

    private static final String COLUMNS = "c.id, c.name, c.description, c.type, c.scope, c.user_id, c.status, c.version";
    private static final Map<String, String> SORTABLE = Map.of(
            "id", "c.id", "name", "c.name", "type", "c.type", "scope", "c.scope", "description", "c.description");

    private static final RowMapper<Category> ROW = (rs, row) -> new Category(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("description"),
            CategoryType.valueOf(rs.getString("type")),
            CategoryScope.valueOf(rs.getString("scope")),
            rs.getObject("user_id", UUID.class),
            CategoryStatus.valueOf(rs.getString("status")),
            rs.getLong("version"));

    private final NamedParameterJdbcTemplate jdbc;

    public CategoryCustomRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** SYSTEM categories plus the caller's own USER ones, optionally of one type. */
    public Page<Category> findVisibleToUser(UUID userId, CategoryType type, Pageable pageable) {
        String where = " WHERE c.status <> 'TRASHED' AND (c.scope = 'SYSTEM' OR (c.scope = 'USER' AND c.user_id = :userId))"
                + (type == null ? "" : " AND c.type = :type");
        return page(where, new MapSqlParameterSource("userId", userId).addValue("type", type == null ? null : type.name()), pageable);
    }

    /** SYSTEM categories only, optionally of one type. */
    public Page<Category> findSystemCategories(CategoryType type, Pageable pageable) {
        String where = " WHERE c.status <> 'TRASHED' AND c.scope = 'SYSTEM'" + (type == null ? "" : " AND c.type = :type");
        return page(where, new MapSqlParameterSource("type", type == null ? null : type.name()), pageable);
    }

    /** One non-trashed category. */
    public Optional<Category> findById(Long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM categories c WHERE c.id = :id AND c.status <> 'TRASHED'",
                new MapSqlParameterSource("id", id), ROW).stream().findFirst();
    }

    private Page<Category> page(String where, MapSqlParameterSource params, Pageable pageable) {
        String sql = "SELECT " + COLUMNS + ", " + JdbcPages.TOTAL_COLUMN + " FROM categories c" + where
                + JdbcPages.orderBy(pageable.getSort(), SORTABLE, "c.id");
        return JdbcPages.page(jdbc, sql, params, pageable, ROW, "SELECT count(*) FROM categories c" + where);
    }
}
