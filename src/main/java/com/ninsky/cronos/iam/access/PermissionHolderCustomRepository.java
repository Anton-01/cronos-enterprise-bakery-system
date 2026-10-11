package com.ninsky.cronos.iam.access;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ACTIVE users who hold a permission through an ACTIVE role (or are SUPER_ADMIN): the audience of administrative
 * notifications such as "a registration waits for review". Group grants and per-user overrides are not considered;
 * an override that denies the permission only hides the screen, it never grants it.
 */
@Repository
public class PermissionHolderCustomRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public PermissionHolderCustomRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Ids of the holders of {@code permission}. */
    public List<UUID> holders(String permission) {
        return jdbc.queryForList("""
                SELECT DISTINCT u.id FROM users u
                JOIN user_roles ur ON ur.user_id = u.id
                JOIN roles r ON r.id = ur.role_id AND r.status = 'ACTIVE'
                LEFT JOIN role_permissions rp ON rp.role_id = r.id
                LEFT JOIN permissions p ON p.id = rp.permission_id
                WHERE u.status = 'ACTIVE' AND (r.code = 'SUPER_ADMIN' OR p.name = :permission)""",
                Map.of("permission", permission), UUID.class);
    }
}
