package com.ninsky.cronos.iam.group.api;

import java.util.List;

/** Create/update body; {@code version} is required on update. */
public record PermissionGroupRequest(String code, String name, String description, List<String> permissions, Long version) {
}
