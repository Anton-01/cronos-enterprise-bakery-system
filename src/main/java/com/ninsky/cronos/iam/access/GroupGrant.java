package com.ninsky.cronos.iam.access;

import java.util.Set;

/** A permission group as seen by the resolver. */
public record GroupGrant(long id, String code, String name, boolean active, Set<String> permissions) {
    public GroupGrant {
        permissions = Set.copyOf(permissions);
    }
}
