package com.ninsky.cronos.iam.shared;

/** Compact role reference (spec §2). */
public record RoleRef(long id, String code, String name, String color) {
}
