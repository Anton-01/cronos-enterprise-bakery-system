package com.ninsky.cronos.iam.access;

/** One origin of a permission; {@code viaName} is the role a ROLE_GROUP came through. */
public record PermissionSource(SourceType type, Long id, String name, String viaName) {

    static PermissionSource role(RoleGrant role) {
        return new PermissionSource(SourceType.ROLE, role.id(), role.name(), null);
    }

    static PermissionSource roleGroup(GroupGrant group, RoleGrant via) {
        return new PermissionSource(SourceType.ROLE_GROUP, group.id(), group.name(), via.name());
    }

    static PermissionSource userGroup(GroupGrant group) {
        return new PermissionSource(SourceType.USER_GROUP, group.id(), group.name(), null);
    }

    static PermissionSource directGrant() {
        return new PermissionSource(SourceType.DIRECT_GRANT, null, null, null);
    }
}
