package com.ninsky.cronos.iam.access;

import java.util.Set;
import java.util.UUID;

/** Published when users' effective access or status changed; caches drop them after commit. */
public record AccessChanged(Set<UUID> userIds) {
    public AccessChanged {
        userIds = Set.copyOf(userIds);
    }
}
