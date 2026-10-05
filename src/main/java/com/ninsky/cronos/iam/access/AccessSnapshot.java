package com.ninsky.cronos.iam.access;

import java.util.List;
import java.util.Set;

/** Everything that feeds one user's effective permissions (spec §1.4.2). */
public record AccessSnapshot(List<RoleGrant> roles, List<GroupGrant> groups, Set<String> grants, Set<String> denials) {
    public AccessSnapshot {
        roles = List.copyOf(roles);
        groups = List.copyOf(groups);
        grants = Set.copyOf(grants);
        denials = Set.copyOf(denials);
    }

    public static AccessSnapshot empty() {
        return new AccessSnapshot(List.of(), List.of(), Set.of(), Set.of());
    }

    public AccessSnapshot withRoles(List<RoleGrant> newRoles) {
        return new AccessSnapshot(newRoles, groups, grants, denials);
    }

    public AccessSnapshot withAssignments(List<RoleGrant> newRoles, List<GroupGrant> newGroups, Set<String> newGrants, Set<String> newDenials) {
        return new AccessSnapshot(newRoles, newGroups, newGrants, newDenials);
    }

    public boolean holdsActiveSuperAdmin() {
        return roles.stream().anyMatch(r -> r.active() && r.isSuperAdmin());
    }
}
