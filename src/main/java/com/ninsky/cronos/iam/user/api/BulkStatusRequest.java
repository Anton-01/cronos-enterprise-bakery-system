package com.ninsky.cronos.iam.user.api;

import com.ninsky.cronos.iam.user.StatusReason;
import com.ninsky.cronos.iam.user.UserStatus;

import java.util.List;
import java.util.UUID;

/** {@code POST /iam/users/bulk/status}. */
public record BulkStatusRequest(List<UUID> userIds, UserStatus status, StatusReason reason, String comment) {
}
