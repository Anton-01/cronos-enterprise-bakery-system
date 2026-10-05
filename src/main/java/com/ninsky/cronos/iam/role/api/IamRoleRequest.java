package com.ninsky.cronos.iam.role.api;

import java.util.List;

/** Create/update body; {@code version} is required on update. */
public record IamRoleRequest(String code, String name, String description, String color, List<String> permissions,
                          List<Long> permissionGroupIds, Long version) {
}
