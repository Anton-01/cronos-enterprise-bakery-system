package com.ninsky.cronos.infrastructure.persistence.recipe.adapter;

import com.ninsky.cronos.domain.model.recipe.UserFixedCost;
import com.ninsky.cronos.infrastructure.persistence.jdbc.JdbcPages;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Read side of {@code user_fixed_costs} in plain SQL: only the columns of {@link UserFixedCost}, page and total in one statement. */
@Repository
public class UserFixedCostCustomRepository {

    private static final String COLUMNS = """
            f.id, f.user_id, f.name, f.description, f.type, f.default_amount, f.percentage, f.calculation_method, f.is_active,
            f.applies_by_default, f.monthly_amount, f.monthly_basis, f.seed_code, f.version, f.created_at, f.updated_at""";
    private static final Map<String, String> SORTABLE = Map.of(
            "id", "f.id", "name", "f.name", "type", "f.type", "defaultAmount", "f.default_amount", "percentage", "f.percentage",
            "calculationMethod", "f.calculation_method", "isActive", "f.is_active", "active", "f.is_active",
            "createdAt", "f.created_at", "updatedAt", "f.updated_at");

    private static final RowMapper<UserFixedCost> ROW = (rs, row) -> UserFixedCost.builder()
            .id(rs.getObject("id", UUID.class))
            .userId(rs.getObject("user_id", UUID.class))
            .name(rs.getString("name"))
            .description(rs.getString("description"))
            .type(rs.getString("type"))
            .defaultAmount(rs.getBigDecimal("default_amount"))
            .percentage(rs.getBigDecimal("percentage"))
            .calculationMethod(rs.getString("calculation_method"))
            .isActive(rs.getBoolean("is_active"))
            .appliesByDefault(rs.getBoolean("applies_by_default"))
            .monthlyAmount(rs.getBigDecimal("monthly_amount"))
            .monthlyBasis(rs.getBigDecimal("monthly_basis"))
            .seedCode(rs.getString("seed_code"))
            .version(rs.getLong("version"))
            .createdAt(local(rs.getTimestamp("created_at")))
            .updatedAt(local(rs.getTimestamp("updated_at")))
            .build();

    private final NamedParameterJdbcTemplate jdbc;

    public UserFixedCostCustomRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UserFixedCost> findByIdAndUserId(UUID id, UUID userId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM user_fixed_costs f WHERE f.id = :id AND f.user_id = :user",
                new MapSqlParameterSource("id", id).addValue("user", userId), ROW).stream().findFirst();
    }

    /** The user's rows whose name contains {@code name} (case-insensitive; null = all). */
    public Page<UserFixedCost> findByUser(UUID userId, String name, Pageable pageable) {
        MapSqlParameterSource params = new MapSqlParameterSource("user", userId);
        String where = " FROM user_fixed_costs f WHERE f.user_id = :user";
        if (name != null) {
            where += " AND lower(f.name) LIKE :pattern ESCAPE '\\'";
            params.addValue("pattern", "%" + UnitLike.escape(name.toLowerCase(Locale.ROOT)) + "%");
        }
        Sort sort = pageable.getSort().isSorted() ? pageable.getSort() : Sort.by("createdAt");
        String sql = "SELECT " + COLUMNS + ", " + JdbcPages.TOTAL_COLUMN + where + JdbcPages.orderBy(sort, SORTABLE, "f.id");
        return JdbcPages.page(jdbc, sql, params, pageable, ROW, "SELECT count(*)" + where);
    }

    private static LocalDateTime local(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    /** LIKE escaping (backslash as escape character). */
    private static final class UnitLike {
        private UnitLike() {
        }

        static String escape(String value) {
            return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        }
    }
}
