package com.ninsky.cronos.iam.group.api;

import com.ninsky.cronos.iam.role.RoleStatus;

import java.time.Instant;

/** Group list row (spec §6). */
public record PermissionGroupSummary(long id, String code, String name, String description, boolean system, RoleStatus status,
                                     long permissionCount, long roleCount, long userCount, Instant updatedAt) {
}
