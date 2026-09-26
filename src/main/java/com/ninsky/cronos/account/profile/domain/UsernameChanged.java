package com.ninsky.cronos.account.profile.domain;

import java.util.UUID;

/** Published inside the profile write; consumed AFTER_COMMIT to drop auth caches keyed by the old name. */
public record UsernameChanged(UUID userId, String previousUsername, String newUsername) {
}
