package com.ninsky.cronos.iam.group;

import com.ninsky.cronos.iam.role.RoleStatus;

import java.time.Instant;

/** One {@code permission_groups} row with counters. */
public record GroupRow(long id, String code, String name, String description, boolean system, RoleStatus status,
                       long permissionCount, long roleCount, long userCount, Instant createdAt, Instant updatedAt, long version) {
}
