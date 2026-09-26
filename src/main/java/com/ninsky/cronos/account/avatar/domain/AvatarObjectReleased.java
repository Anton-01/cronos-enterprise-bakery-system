package com.ninsky.cronos.account.avatar.domain;

import java.util.UUID;

/**
 * A stored avatar object is no longer referenced by the user's committed row and may be deleted.
 * Published inside the write transaction, acted on only AFTER_COMMIT, so a rollback never leaves
 * the user pointing at a deleted object.
 */
public record AvatarObjectReleased(UUID userId, AvatarKey key) {
}
