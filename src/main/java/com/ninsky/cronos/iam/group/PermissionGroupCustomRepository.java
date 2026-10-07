package com.ninsky.cronos.iam.group;

import com.ninsky.cronos.iam.role.RoleStatus;
import com.ninsky.cronos.iam.shared.RoleRef;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** JDBC access to permission groups. */
@Repository
@RequiredArgsConstructor
public class PermissionGroupCustomRepository {

    private static final String SELECT = """
            SELECT g.id, g.code, g.name, g.description, g.system, g.status, g.created_at, g.updated_at, g.version,
                   (SELECT count(*) FROM permission_group_permissions gp JOIN permissions p ON p.name = gp.permission_code
                    WHERE gp.group_id = g.id AND NOT p.deprecated) AS permission_count,
                   (SELECT count(*) FROM role_permission_groups rpg WHERE rpg.group_id = g.id) AS role_count,
                   (SELECT count(*) FROM user_permission_groups upg WHERE upg.group_id = g.id) AS user_count
            FROM permission_groups g
            """;

    private static final RowMapper<GroupRow> ROW = (rs, i) -> new GroupRow(rs.getLong("id"), rs.getString("code"),
            rs.getString("name"), rs.getString("description"), rs.getBoolean("system"), RoleStatus.valueOf(rs.getString("status")),
            rs.getLong("permission_count"), rs.getLong("role_count"), rs.getLong("user_count"),
            instant(rs.getObject("created_at", OffsetDateTime.class)), instant(rs.getObject("updated_at", OffsetDateTime.class)),
            rs.getLong("version"));

    private final NamedParameterJdbcTemplate jdbc;

    public List<GroupRow> findAll() {
        return jdbc.query(SELECT + " ORDER BY lower(g.name), g.id", ROW);
    }

    public Optional<GroupRow> find(long id) {
        return jdbc.query(SELECT + " WHERE g.id = :id", Map.of("id", id), ROW).stream().findFirst();
    }

    public Optional<GroupRow> lock(long id) {
        jdbc.query("SELECT id FROM permission_groups WHERE id = :id FOR UPDATE", Map.of("id", id), rs -> { });
        return find(id);
    }

    public Set<String> permissions(long id) {
        return Set.copyOf(jdbc.queryForList("""
                SELECT gp.permission_code FROM permission_group_permissions gp JOIN permissions p ON p.name = gp.permission_code
                WHERE gp.group_id = :id AND NOT p.deprecated""", Map.of("id", id), String.class));
    }

    public List<RoleRef> roles(long id) {
        return jdbc.query("""
                        SELECT r.id, r.code, r.name, r.color FROM role_permission_groups rpg JOIN roles r ON r.id = rpg.role_id
                        WHERE rpg.group_id = :id ORDER BY lower(r.name)""", Map.of("id", id),
                (rs, i) -> new RoleRef(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4)));
    }

    public boolean codeTaken(String code, Long excludeId) {
        return exists("SELECT EXISTS (SELECT 1 FROM permission_groups WHERE code = :value AND id <> coalesce(:exclude, -1))", code, excludeId);
    }

    public boolean nameTaken(String name, Long excludeId) {
        return exists("SELECT EXISTS (SELECT 1 FROM permission_groups WHERE lower(name) = lower(:value) AND id <> coalesce(:exclude, -1))", name, excludeId);
    }

    public long insert(String code, String name, String description, UUID actor) {
        var keys = new GeneratedKeyHolder();
        jdbc.update("""
                        INSERT INTO permission_groups (code, name, description, system, status, version, created_by)
                        VALUES (:code, :name, :description, FALSE, 'ACTIVE', 0, :actor)""",
                new MapSqlParameterSource("code", code).addValue("name", name).addValue("description", description).addValue("actor", actor),
                keys, new String[]{"id"});
        return Objects.requireNonNull(keys.getKey()).longValue();
    }

    public boolean update(long id, String code, String name, String description, long version, UUID actor) {
        return jdbc.update("""
                        UPDATE permission_groups SET code = :code, name = :name, description = :description,
                               version = version + 1, updated_at = now(), updated_by = :actor
                        WHERE id = :id AND version = :version""",
                new MapSqlParameterSource("id", id).addValue("code", code).addValue("name", name)
                        .addValue("description", description).addValue("version", version).addValue("actor", actor)) == 1;
    }

    public boolean updateStatus(long id, RoleStatus status, long version, UUID actor) {
        return jdbc.update("""
                        UPDATE permission_groups SET status = :status, version = version + 1, updated_at = now(), updated_by = :actor
                        WHERE id = :id AND version = :version""",
                new MapSqlParameterSource("id", id).addValue("status", status.name()).addValue("version", version)
                        .addValue("actor", actor)) == 1;
    }

    public void replacePermissions(long id, Collection<String> codes) {
        var params = new MapSqlParameterSource("id", id).addValue("codes", codes.toArray(String[]::new));
        jdbc.update("DELETE FROM permission_group_permissions WHERE group_id = :id AND NOT (permission_code = ANY(:codes))", params);
        jdbc.update("""
                INSERT INTO permission_group_permissions (group_id, permission_code)
                SELECT :id, unnest(CAST(:codes AS VARCHAR[])) ON CONFLICT DO NOTHING""", params);
    }

    public void delete(long id) {
        jdbc.update("DELETE FROM permission_groups WHERE id = :id", Map.of("id", id));
    }

    private boolean exists(String sql, String value, Long excludeId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(sql,
                new MapSqlParameterSource("value", value).addValue("exclude", excludeId), Boolean.class));
    }

    private static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
