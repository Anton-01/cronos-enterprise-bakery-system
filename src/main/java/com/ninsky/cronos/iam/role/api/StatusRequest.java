package com.ninsky.cronos.iam.role.api;

import com.ninsky.cronos.iam.role.RoleStatus;

/** {@code PATCH …/status} body for roles and permission groups. */
public record StatusRequest(RoleStatus status, Long version) {
}
