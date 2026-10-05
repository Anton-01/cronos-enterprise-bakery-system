package com.ninsky.cronos.iam.token;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Single-use tokens stored as SHA-256 hashes (spec §3.5). */
public interface UserTokens {

    /** 32 random bytes; previous unused tokens of the same purpose are invalidated. */
    IssuedToken issue(UUID userId, TokenPurpose purpose, Duration ttl);

    /** Marks the token used and returns its user when it is valid, unused and unexpired. */
    Optional<UUID> consume(String rawToken, TokenPurpose purpose);

    /** Expiry of the newest unused, unexpired token of that purpose. */
    Optional<Instant> pendingExpiry(UUID userId, TokenPurpose purpose);

    /** Invalidates every unused token of that purpose. */
    void invalidate(UUID userId, TokenPurpose purpose);
}
