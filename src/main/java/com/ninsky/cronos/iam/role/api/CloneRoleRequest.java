package com.ninsky.cronos.iam.role.api;

/** {@code POST /iam/roles/{id}/clone} body. */
public record CloneRoleRequest(String code, String name) {
}
