package com.ninsky.cronos.iam.user.api;

import com.ninsky.cronos.iam.access.EffectiveEntry;
import com.ninsky.cronos.iam.shared.PermissionGroupRef;
import com.ninsky.cronos.iam.shared.RoleRef;
import com.ninsky.cronos.iam.sod.SodConflict;

import java.util.List;

/** {@code UserAccess} (spec §3.6); {@code version} is the user's version. */
public record UserAccessView(List<RoleRef> roles, List<PermissionGroupRef> permissionGroups, List<String> grants,
                             List<String> denials, List<EffectiveEntry> effective, List<SodConflict> sodConflicts,
                             long version) {
}
