package com.ninsky.cronos.iam.user.api;

import com.ninsky.cronos.iam.user.StatusReason;
import com.ninsky.cronos.iam.user.UserStatus;

import java.time.Instant;

/** {@code POST /iam/users/{id}/status}. */
public record UserStatusRequest(UserStatus status, StatusReason reason, String comment, Instant until,
                                Boolean revokeSessions, Long version) {
}
