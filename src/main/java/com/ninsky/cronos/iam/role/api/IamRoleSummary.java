package com.ninsky.cronos.iam.role.api;

import com.ninsky.cronos.iam.role.RoleStatus;

import java.time.Instant;

/** Role list row (spec §4.1). */
public record IamRoleSummary(long id, String code, String name, String description, String color, boolean system,
                             RoleStatus status, long userCount, long permissionCount, long permissionGroupCount,
                             Instant updatedAt) {
}
