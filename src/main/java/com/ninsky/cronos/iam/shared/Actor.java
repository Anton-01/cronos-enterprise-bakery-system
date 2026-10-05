package com.ninsky.cronos.iam.shared;

import java.util.Set;
import java.util.UUID;

/** The authenticated caller with their effective permission codes. */
public record Actor(UUID id, String username, Set<String> permissions, boolean superAdmin) {

    public Actor {
        permissions = Set.copyOf(permissions);
    }

    public boolean holds(String code) {
        return superAdmin || permissions.contains(code);
    }
}
