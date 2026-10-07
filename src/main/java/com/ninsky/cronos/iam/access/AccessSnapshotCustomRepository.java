package com.ninsky.cronos.iam.access;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Loads the resolver inputs with a handful of set-based queries. */
@Repository
@RequiredArgsConstructor
public class AccessSnapshotCustomRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public AccessSnapshot load(UUID userId) {
        var params = new MapSqlParameterSource("userId", userId);
        List<Long> roleIds = jdbc.queryForList("SELECT role_id FROM user_roles WHERE user_id = :userId", params, Long.class);
        List<Long> groupIds = jdbc.queryForList("SELECT group_id FROM user_permission_groups WHERE user_id = :userId", params, Long.class);
        Set<String> grants = new HashSet<>();
        Set<String> denials = new HashSet<>();
        jdbc.query("SELECT permission_code, effect FROM user_permission_overrides WHERE user_id = :userId", params, rs -> {
            ("DENY".equals(rs.getString(2)) ? denials : grants).add(rs.getString(1));
        });
        return new AccessSnapshot(loadRoles(roleIds), loadGroups(groupIds), grants, denials);
    }

    /** Roles by id (unknown ids are absent), each with its groups. */
    public List<RoleGrant> loadRoles(Collection<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return List.of();
        }
        var params = new MapSqlParameterSource("ids", roleIds);
        Map<Long, Set<String>> permissions = new HashMap<>();
        jdbc.query("""
                SELECT rp.role_id, p.name FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id
                WHERE rp.role_id IN (:ids)""", params, rs -> {
            permissions.computeIfAbsent(rs.getLong(1), k -> new HashSet<>()).add(rs.getString(2));
        });
        Map<Long, List<Long>> roleGroupIds = new HashMap<>();
        jdbc.query("SELECT role_id, group_id FROM role_permission_groups WHERE role_id IN (:ids)", params, rs -> {
            roleGroupIds.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(rs.getLong(2));
        });
        Map<Long, GroupGrant> groups = loadGroups(roleGroupIds.values().stream().flatMap(List::stream).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(GroupGrant::id, g -> g));
        return jdbc.query("SELECT id, code, name, status FROM roles WHERE id IN (:ids) ORDER BY name", params, (rs, i) -> {
            long id = rs.getLong(1);
            return new RoleGrant(id, rs.getString(2), rs.getString(3), "ACTIVE".equals(rs.getString(4)),
                    permissions.getOrDefault(id, Set.of()),
                    roleGroupIds.getOrDefault(id, List.of()).stream().map(groups::get).filter(Objects::nonNull).toList());
        });
    }

    /** Groups by id (unknown ids are absent). */
    public List<GroupGrant> loadGroups(Collection<Long> groupIds) {
        if (groupIds.isEmpty()) {
            return List.of();
        }
        var params = new MapSqlParameterSource("ids", groupIds);
        Map<Long, Set<String>> permissions = new HashMap<>();
        jdbc.query("SELECT group_id, permission_code FROM permission_group_permissions WHERE group_id IN (:ids)", params, rs -> {
            permissions.computeIfAbsent(rs.getLong(1), k -> new HashSet<>()).add(rs.getString(2));
        });
        Map<Long, GroupGrant> byId = new LinkedHashMap<>();
        jdbc.query("SELECT id, code, name, status FROM permission_groups WHERE id IN (:ids) ORDER BY name", params, rs -> {
            long id = rs.getLong(1);
            byId.put(id, new GroupGrant(id, rs.getString(2), rs.getString(3), "ACTIVE".equals(rs.getString(4)),
                    permissions.getOrDefault(id, Set.of())));
        });
        return List.copyOf(byId.values());
    }
}
