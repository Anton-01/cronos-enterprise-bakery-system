package com.ninsky.cronos.infrastructure.persistence.menu;

import com.ninsky.cronos.domain.model.menu.MenuNode;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Cached raw rows of {@code menu_items}; a separate bean so {@code @Cacheable} goes through Spring's proxy. */
@Repository
public class MenuCustomRepository {

    private static final String SELECT_ACTIVE_ITEMS = """
            SELECT id, parent_id, code, label_en, label_es, icon, path, display_order, required_permission
            FROM menu_items
            WHERE is_active = TRUE
            ORDER BY display_order ASC
            """;

    private static final RowMapper<MenuNode> ROW_MAPPER = (rs, rowNum) -> {
        // ResultSet.wasNull() only reflects the MOST RECENTLY read column, so it must be checked
        // immediately after reading parent_id, before any other rs.getXxx() call touches it.
        long rawParentId = rs.getLong("parent_id");
        Long parentId = rs.wasNull() ? null : rawParentId;
        return MenuNode.builder()
                .id(rs.getLong("id"))
                .parentId(parentId)
                .code(rs.getString("code"))
                .labelEn(rs.getString("label_en"))
                .labelEs(rs.getString("label_es"))
                .icon(rs.getString("icon"))
                .path(rs.getString("path"))
                .displayOrder(rs.getInt("display_order"))
                .requiredPermission(rs.getString("required_permission"))
                .build();
    };

    private final JdbcTemplate jdbcTemplate;

    public MenuCustomRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Cacheable("menuItems")
    public List<MenuNode> fetchAllActive() {
        return jdbcTemplate.query(SELECT_ACTIVE_ITEMS, ROW_MAPPER);
    }
}
