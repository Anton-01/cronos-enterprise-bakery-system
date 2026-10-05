package com.ninsky.cronos.iam.role.api;

import java.util.List;
import java.util.UUID;

/** Add/remove members body (spec §4.3). */
public record MembersRequest(List<UUID> userIds, String reason) {
}
