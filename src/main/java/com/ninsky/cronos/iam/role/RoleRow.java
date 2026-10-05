package com.ninsky.cronos.iam.role;

import java.time.Instant;
import java.util.UUID;

/** One {@code roles} row with its counters. */
public record RoleRow(long id, String code, String name, String description, String color, boolean system,
                      RoleStatus status, long userCount, long permissionCount, long permissionGroupCount,
                      Instant createdAt, Instant updatedAt, UUID createdById, UUID updatedById, long version) {

    public boolean isSuperAdmin() {
        return SystemRole.SUPER_ADMIN_CODE.equals(code);
    }
}
