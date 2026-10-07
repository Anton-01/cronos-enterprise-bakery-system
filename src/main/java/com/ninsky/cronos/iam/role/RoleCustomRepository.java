package com.ninsky.cronos.iam.role;

import com.ninsky.cronos.iam.shared.PermissionGroupRef;
import com.ninsky.cronos.iam.shared.TenantTime;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** JDBC access to roles, their permissions, groups and members. */
@Repository
@RequiredArgsConstructor
public class RoleCustomRepository {

    private static final String SELECT = """
            SELECT r.id, r.code, r.name, r.description, r.color, r.system, r.status, r.created_at, r.updated_at,
                   r.created_by_id, r.updated_by_id, r.version,
                   (SELECT count(*) FROM user_roles ur JOIN users u ON u.id = ur.user_id
                    WHERE ur.role_id = r.id AND u.status <> 'DEACTIVATED') AS user_count,
                   (SELECT count(*) FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id
                    WHERE rp.role_id = r.id AND NOT p.deprecated) AS permission_count,
                   (SELECT count(*) FROM role_permission_groups g WHERE g.role_id = r.id) AS group_count
            FROM roles r
            """;

    private static final RowMapper<RoleRow> ROW = (rs, i) -> new RoleRow(
            rs.getLong("id"), rs.getString("code"), rs.getString("name"), rs.getString("description"), rs.getString("color"),
            rs.getBoolean("system"), RoleStatus.valueOf(rs.getString("status")), rs.getLong("user_count"),
            rs.getLong("permission_count"), rs.getLong("group_count"),
            instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("updated_at")),
            rs.getObject("created_by_id", UUID.class), rs.getObject("updated_by_id", UUID.class), rs.getLong("version"));

    private final NamedParameterJdbcTemplate jdbc;

    public List<RoleRow> search(String search, RoleStatus status) {
        return jdbc.query(SELECT + """
                        WHERE (CAST(:search AS TEXT) IS NULL OR unaccent(lower(r.name)) LIKE unaccent(lower(:search))
                               OR lower(r.code) LIKE lower(:search))
                          AND (CAST(:status AS TEXT) IS NULL OR r.status = :status)
                        ORDER BY lower(r.name), r.id""",
                new MapSqlParameterSource("search", search == null ? null : "%" + search + "%")
                        .addValue("status", status == null ? null : status.name()), ROW);
    }

    public Optional<RoleRow> find(long id) {
        return jdbc.query(SELECT + " WHERE r.id = :id", Map.of("id", id), ROW).stream().findFirst();
    }

    /** Lock the row for the rest of the transaction. */
    public Optional<RoleRow> lock(long id) {
        jdbc.query("SELECT id FROM roles WHERE id = :id FOR UPDATE", Map.of("id", id), rs -> { });
        return find(id);
    }

    public Set<String> permissions(long roleId) {
        return Set.copyOf(jdbc.queryForList("""
                SELECT p.name FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id
                WHERE rp.role_id = :id AND NOT p.deprecated""", Map.of("id", roleId), String.class));
    }

    public List<PermissionGroupRef> groups(long roleId) {
        return jdbc.query("""
                        SELECT g.id, g.code, g.name FROM role_permission_groups rpg JOIN permission_groups g ON g.id = rpg.group_id
                        WHERE rpg.role_id = :id ORDER BY lower(g.name)""", Map.of("id", roleId),
                (rs, i) -> new PermissionGroupRef(rs.getLong(1), rs.getString(2), rs.getString(3)));
    }

    public boolean codeTaken(String code, Long excludeId) {
        return exists("SELECT EXISTS (SELECT 1 FROM roles WHERE code = :value AND id <> coalesce(:exclude, -1))", code, excludeId);
    }

    public boolean nameTaken(String name, Long excludeId) {
        return exists("SELECT EXISTS (SELECT 1 FROM roles WHERE lower(name) = lower(:value) AND id <> coalesce(:exclude, -1))", name, excludeId);
    }

    public long insert(String code, String name, String description, String color, UUID actorId, String actorUsername, LocalDateTime now) {
        var keys = new GeneratedKeyHolder();
        jdbc.update("""
                        INSERT INTO roles (code, name, description, color, system, status, version, created_at, created_by, created_by_id)
                        VALUES (:code, :name, :description, :color, FALSE, 'ACTIVE', 0, :now, :username, :actor)""",
                new MapSqlParameterSource("code", code).addValue("name", name).addValue("description", description)
                        .addValue("color", color).addValue("now", now).addValue("username", actorUsername).addValue("actor", actorId),
                keys, new String[]{"id"});
        return Objects.requireNonNull(keys.getKey()).longValue();
    }

    /** @return false when {@code version} is stale */
    public boolean update(long id, String code, String name, String description, String color, long version,
                          UUID actorId, String actorUsername, LocalDateTime now) {
        return jdbc.update("""
                        UPDATE roles SET code = :code, name = :name, description = :description, color = :color,
                               version = version + 1, updated_at = :now, updated_by = :username, updated_by_id = :actor
                        WHERE id = :id AND version = :version""",
                new MapSqlParameterSource("id", id).addValue("code", code).addValue("name", name)
                        .addValue("description", description).addValue("color", color).addValue("version", version)
                        .addValue("now", now).addValue("username", actorUsername).addValue("actor", actorId)) == 1;
    }

    public boolean updateStatus(long id, RoleStatus status, long version, UUID actorId, String actorUsername, LocalDateTime now) {
        return jdbc.update("""
                        UPDATE roles SET status = :status, version = version + 1, updated_at = :now, updated_by = :username,
                               updated_by_id = :actor WHERE id = :id AND version = :version""",
                new MapSqlParameterSource("id", id).addValue("status", status.name()).addValue("version", version)
                        .addValue("now", now).addValue("username", actorUsername).addValue("actor", actorId)) == 1;
    }

    public void replacePermissions(long roleId, Collection<String> codes) {
        var params = new MapSqlParameterSource("id", roleId).addValue("codes", codes.toArray(String[]::new));
        jdbc.update("""
                DELETE FROM role_permissions rp USING permissions p
                WHERE rp.permission_id = p.id AND rp.role_id = :id AND NOT p.deprecated AND NOT (p.name = ANY(:codes))""", params);
        jdbc.update("""
                INSERT INTO role_permissions (role_id, permission_id)
                SELECT :id, p.id FROM permissions p WHERE p.name = ANY(:codes) ON CONFLICT DO NOTHING""", params);
    }

    public void replaceGroups(long roleId, Collection<Long> groupIds) {
        var params = new MapSqlParameterSource("id", roleId).addValue("ids", groupIds.toArray(Long[]::new));
        jdbc.update("DELETE FROM role_permission_groups WHERE role_id = :id AND NOT (group_id = ANY(:ids))", params);
        jdbc.update("""
                INSERT INTO role_permission_groups (role_id, group_id)
                SELECT :id, unnest(CAST(:ids AS BIGINT[])) ON CONFLICT DO NOTHING""", params);
    }

    /** Removes the role with its memberships (only DEACTIVATED users can still hold it at this point). */
    public void delete(long roleId) {
        var params = Map.of("id", roleId);
        jdbc.update("DELETE FROM user_roles WHERE role_id = :id", params);
        jdbc.update("DELETE FROM role_permissions WHERE role_id = :id", params);
        jdbc.update("DELETE FROM roles WHERE id = :id", params);
    }

    public List<UUID> memberIds(long roleId) {
        return jdbc.queryForList("SELECT user_id FROM user_roles WHERE role_id = :id", Map.of("id", roleId), UUID.class);
    }

    private boolean exists(String sql, String value, Long excludeId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(sql,
                new MapSqlParameterSource("value", value).addValue("exclude", excludeId), Boolean.class));
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : TenantTime.toInstant(timestamp.toLocalDateTime());
    }
}
