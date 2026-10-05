package com.ninsky.cronos.iam.access;

import com.ninsky.cronos.iam.role.SystemRole;

import java.util.List;
import java.util.Set;

/** A role with its direct permissions and attached groups. */
public record RoleGrant(long id, String code, String name, boolean active, Set<String> permissions, List<GroupGrant> groups) {
    public RoleGrant {
        permissions = Set.copyOf(permissions);
        groups = List.copyOf(groups);
    }

    public boolean isSuperAdmin() {
        return SystemRole.SUPER_ADMIN_CODE.equals(code);
    }
}
