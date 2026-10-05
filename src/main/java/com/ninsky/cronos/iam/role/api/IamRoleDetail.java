package com.ninsky.cronos.iam.role.api;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.ninsky.cronos.iam.shared.PermissionGroupRef;
import com.ninsky.cronos.iam.shared.UserRef;
import com.ninsky.cronos.iam.sod.SodConflict;

import java.time.Instant;
import java.util.List;

/** Role detail (spec §4.1). */
public record IamRoleDetail(@JsonUnwrapped IamRoleSummary summary, List<String> permissions,
                            List<PermissionGroupRef> permissionGroups, List<String> effectivePermissions,
                            List<SodConflict> sodConflicts, Instant createdAt, UserRef createdBy, UserRef updatedBy,
                            long version) {
}
