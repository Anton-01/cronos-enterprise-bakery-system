package com.ninsky.cronos.iam.access;

import com.ninsky.cronos.iam.permission.PermissionCatalog;
import com.ninsky.cronos.iam.permission.Permissions;

import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/** Cached per (user, access version): active role codes and the resolved access. */
public record UserAccessState(long accessVersion, List<String> roleCodes, EffectiveAccess access) {

    public UserAccessState {
        roleCodes = List.copyOf(roleCodes);
    }

    /** Claim/authority set: effective codes plus the transitional MANAGE_CATALOGS alias (§1.4.4). */
    public SortedSet<String> permissionClaim() {
        SortedSet<String> claim = new TreeSet<>(access.granted());
        boolean managesCatalogs = PermissionCatalog.matching("CATALOG", null, "MANAGE").stream().anyMatch(claim::contains);
        if (managesCatalogs) {
            claim.add(Permissions.LEGACY_MANAGE_CATALOGS);
        }
        return claim;
    }

    public boolean superAdmin() {
        return access.superAdmin();
    }
}
