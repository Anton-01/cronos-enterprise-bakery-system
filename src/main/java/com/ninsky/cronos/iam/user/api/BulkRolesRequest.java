package com.ninsky.cronos.iam.user.api;

import java.util.List;
import java.util.UUID;

/** {@code POST /iam/users/bulk/roles}. */
public record BulkRolesRequest(List<UUID> userIds, List<Long> addRoleIds, List<Long> removeRoleIds, String reason) {
}
