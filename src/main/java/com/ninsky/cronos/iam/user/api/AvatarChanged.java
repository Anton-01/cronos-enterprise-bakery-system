package com.ninsky.cronos.iam.user.api;

import java.time.Instant;

/** Result of an admin avatar upload. */
public record AvatarChanged(String avatarUrl, Instant updatedAt) {
}
