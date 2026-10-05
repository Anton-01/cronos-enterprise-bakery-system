package com.ninsky.cronos.iam.shared;

/** Compact permission group reference (spec §2). */
public record PermissionGroupRef(long id, String code, String name) {
}
