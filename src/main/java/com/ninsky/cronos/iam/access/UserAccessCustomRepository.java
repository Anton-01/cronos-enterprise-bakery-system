package com.ninsky.cronos.iam.access;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** SQL behind user access: memberships, overrides, access versions and root-protection checks. */
@Repository
@RequiredArgsConstructor
public class UserAccessCustomRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /** Users of {@code candidates} holding the role {@code roleCode}. */
    public List<UUID> holdersAmong(String roleCode, Collection<UUID> candidates) {
        return jdbc.queryForList("""
                        SELECT ur.user_id FROM user_roles ur JOIN roles r ON r.id = ur.role_id
                        WHERE r.code = :code AND ur.user_id IN (:ids)""",
                new MapSqlParameterSource("code", roleCode).addValue("ids", Set.copyOf(candidates)), UUID.class);
    }

    /** Whether anyone holds the role {@code roleCode}. */
    public boolean anyHolder(String roleCode) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM user_roles ur JOIN roles r ON r.id = ur.role_id WHERE r.code = :code)""",
                new MapSqlParameterSource("code", roleCode), Boolean.class));
    }

    /** ACTIVE users keeping an ACTIVE {@code roleCode} membership, excluding {@code leaving}. */
    public int activeHoldersExcluding(String roleCode, Collection<UUID> leaving) {
        Integer remaining = jdbc.queryForObject("""
                        SELECT count(DISTINCT u.id) FROM users u JOIN user_roles ur ON ur.user_id = u.id JOIN roles r ON r.id = ur.role_id
                        WHERE r.code = :code AND r.status = 'ACTIVE' AND u.status = 'ACTIVE' AND u.id NOT IN (:ids)""",
                new MapSqlParameterSource("code", roleCode).addValue("ids", Set.copyOf(leaving)), Integer.class);
        return remaining == null ? 0 : remaining;
    }

    /** -1 when the user does not exist. */
    public long accessVersion(UUID userId) {
        List<Long> rows = jdbc.queryForList("SELECT access_version FROM users WHERE id = :id", new MapSqlParameterSource("id", userId), Long.class);
        return rows.isEmpty() ? -1 : rows.getFirst();
    }

    public void bumpAccessVersions(Collection<UUID> userIds) {
        jdbc.update("UPDATE users SET access_version = access_version + 1 WHERE id IN (:ids)",
                new MapSqlParameterSource("ids", Set.copyOf(userIds)));
    }

    /** Users holding any of the roles (any status). */
    public List<UUID> membersOfRoles(Collection<Long> roleIds) {
        return jdbc.queryForList("SELECT DISTINCT user_id FROM user_roles WHERE role_id IN (:ids)",
                new MapSqlParameterSource("ids", roleIds), UUID.class);
    }

    /** Direct holders of a group plus members of every role that includes it. */
    public List<UUID> affectedByGroup(long groupId) {
        return jdbc.queryForList("""
                SELECT user_id FROM user_permission_groups WHERE group_id = :id
                UNION
                SELECT ur.user_id FROM user_roles ur JOIN role_permission_groups rpg ON rpg.role_id = ur.role_id
                WHERE rpg.group_id = :id""", new MapSqlParameterSource("id", groupId), UUID.class);
    }

    public boolean userExists(UUID userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM users WHERE id = :id)", Map.of("id", userId), Boolean.class));
    }

    /** Bumps the user's version; false when {@code expectedVersion} is stale. */
    public boolean touchUser(UUID userId, UUID actor, LocalDateTime now, Long expectedVersion) {
        return jdbc.update("""
                        UPDATE users SET version = version + 1, updated_at = :now, updated_by_id = :actor
                        WHERE id = :id AND (CAST(:expected AS BIGINT) IS NULL OR version = :expected)""",
                new MapSqlParameterSource("id", userId).addValue("actor", actor).addValue("now", now)
                        .addValue("expected", expectedVersion)) > 0;
    }

    /** Leaves the user with exactly {@code roleIds}. */
    public void replaceRoles(UUID userId, Set<Long> roleIds, UUID actor) {
        jdbc.update("DELETE FROM user_roles WHERE user_id = :userId AND NOT (role_id = ANY(:ids))",
                new MapSqlParameterSource("userId", userId).addValue("ids", roleIds.toArray(Long[]::new)));
        roleIds.forEach(id -> jdbc.update("""
                INSERT INTO user_roles (user_id, role_id, created_by_id) VALUES (:userId, :roleId, :actor)
                ON CONFLICT DO NOTHING""", new MapSqlParameterSource("userId", userId).addValue("roleId", id).addValue("actor", actor)));
    }

    /** Leaves the user with exactly {@code groupIds}. */
    public void replaceGroups(UUID userId, Set<Long> groupIds, UUID actor) {
        jdbc.update("DELETE FROM user_permission_groups WHERE user_id = :userId AND NOT (group_id = ANY(:ids))",
                new MapSqlParameterSource("userId", userId).addValue("ids", groupIds.toArray(Long[]::new)));
        groupIds.forEach(id -> jdbc.update("""
                INSERT INTO user_permission_groups (user_id, group_id, created_by_id) VALUES (:userId, :groupId, :actor)
                ON CONFLICT DO NOTHING""", new MapSqlParameterSource("userId", userId).addValue("groupId", id).addValue("actor", actor)));
    }

    /** Leaves the user with exactly {@code overrides} (code → GRANT | DENY). */
    public void replaceOverrides(UUID userId, Map<String, String> overrides, UUID actor) {
        jdbc.update("DELETE FROM user_permission_overrides WHERE user_id = :userId AND NOT (permission_code = ANY(:codes))",
                new MapSqlParameterSource("userId", userId).addValue("codes", overrides.keySet().toArray(String[]::new)));
        overrides.forEach((code, effect) -> jdbc.update("""
                INSERT INTO user_permission_overrides (user_id, permission_code, effect, created_by_id)
                VALUES (:userId, :code, :effect, :actor)
                ON CONFLICT (user_id, permission_code) DO UPDATE SET effect = EXCLUDED.effect
                WHERE user_permission_overrides.effect <> EXCLUDED.effect""",
                new MapSqlParameterSource("userId", userId).addValue("code", code).addValue("effect", effect).addValue("actor", actor)));
    }

    /** Profile full name, else the username. */
    public String userLabel(UUID userId) {
        return jdbc.queryForObject("""
                SELECT coalesce(NULLIF(btrim(concat_ws(' ', p.first_name, p.last_name)), ''), u.username)
                FROM users u LEFT JOIN user_profiles p ON p.user_id = u.id WHERE u.id = :id""", Map.of("id", userId), String.class);
    }
}
