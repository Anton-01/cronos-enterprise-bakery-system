package com.ninsky.cronos.iam.access;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** {@code users.access_version}: read through a short-lived cache, bumped on access changes (§1.4.5). */
@Component
@RequiredArgsConstructor
public class AccessVersions {

    private final UserAccessCustomRepository repository;
    private final ApplicationEventPublisher events;

    @Cacheable(value = AccessCaches.ACCESS_VERSION, key = "#userId")
    public long current(UUID userId) {
        return repository.accessVersion(userId);
    }

    /** Bumps every listed user in one statement; caches drop them after commit. */
    public void bump(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return;
        }
        repository.bumpAccessVersions(userIds);
        events.publishEvent(new AccessChanged(Set.copyOf(userIds)));
    }

    /** Users holding a role (any status), for role-wide bumps. */
    public List<UUID> membersOfRoles(Collection<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return repository.membersOfRoles(roleIds);
    }

    /** Direct holders of a group plus members of every role that includes it. */
    public List<UUID> affectedByGroup(long groupId) {
        return repository.affectedByGroup(groupId);
    }
}
