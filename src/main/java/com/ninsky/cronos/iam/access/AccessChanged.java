package com.ninsky.cronos.iam.access;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Published when users' effective access or status changed; caches drop them after commit.
 *
 * @param versions the new access version of each user, published to the session state so older tokens are refused
 */
public record AccessChanged(Set<UUID> userIds, Map<UUID, Long> versions) {
    public AccessChanged {
        userIds = Set.copyOf(userIds);
        versions = Map.copyOf(versions);
    }

    /** A change whose new versions are unknown (caches still drop the users). */
    public AccessChanged(Set<UUID> userIds) {
        this(userIds, Map.of());
    }
}
