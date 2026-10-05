package com.ninsky.cronos.iam.access;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** {@code users.access_version}: read through a short-lived cache, bumped on access changes (§1.4.5). */
@Component
@RequiredArgsConstructor
public class AccessVersions {

    private final NamedParameterJdbcTemplate jdbc;
    private final ApplicationEventPublisher events;

    @Cacheable(value = AccessCaches.ACCESS_VERSION, key = "#userId")
    public long current(UUID userId) {
        List<Long> rows = jdbc.queryForList("SELECT access_version FROM users WHERE id = :id",
                new MapSqlParameterSource("id", userId), Long.class);
        return rows.isEmpty() ? -1 : rows.getFirst();
    }

    /** Bumps every listed user in one statement; caches drop them after commit. */
    public void bump(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return;
        }
        jdbc.update("UPDATE users SET access_version = access_version + 1 WHERE id IN (:ids)",
                new MapSqlParameterSource("ids", Set.copyOf(userIds)));
        events.publishEvent(new AccessChanged(Set.copyOf(userIds)));
    }

    /** Users holding a role (any status), for role-wide bumps. */
    public List<UUID> membersOfRoles(Collection<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return List.of();
        }
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
}
