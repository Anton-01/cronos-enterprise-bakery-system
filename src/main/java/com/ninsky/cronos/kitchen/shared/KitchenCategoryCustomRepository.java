package com.ninsky.cronos.kitchen.shared;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Category checks and names for kitchen rows (types INGREDIENT / PRODUCT). */
@Repository
@RequiredArgsConstructor
public class KitchenCategoryCustomRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /** ACTIVE, not trashed, of {@code type}, SYSTEM or the tenant's. */
    public boolean usable(UUID tenant, long categoryId, String type) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM categories WHERE id = :id AND type = :type AND status = 'ACTIVE' AND deleted_at IS NULL
                    AND (user_id IS NULL OR user_id = :tenant))""",
                new MapSqlParameterSource().addValue("id", categoryId).addValue("type", type).addValue("tenant", tenant), Boolean.class));
    }

    public Map<Long, String> names(Collection<Long> ids) {
        Map<Long, String> names = new HashMap<>();
        if (ids.isEmpty()) {
            return names;
        }
        jdbc.query("SELECT id, name FROM categories WHERE id IN (:ids)", Map.of("ids", Set.copyOf(ids)),
                rs -> {
                    names.put(rs.getLong("id"), rs.getString("name"));
                });
        return names;
    }
}
