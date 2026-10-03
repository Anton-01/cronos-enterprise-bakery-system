package com.ninsky.cronos.domain.model.audit;

import java.util.Objects;
import java.util.UUID;

/** Who performs a write: stamped into audit entries and import batches. */
public record Actor(UUID userId, String username) {

    public Actor {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(username, "username");
    }
}
