package com.ninsky.cronos.account.avatar.application.port;

import com.ninsky.cronos.account.avatar.domain.AvatarKey;

import java.net.URI;
import java.util.Optional;

/**
 * Object storage for avatar JPEGs. Every adapter (local filesystem, GCS, in-memory for tests) must
 * satisfy the same contract, verified by one shared abstract test ({@code AvatarStorageContractTest}):
 * <ul>
 *   <li>{@link #put} is idempotent for the same key (content-addressed: same key ⇒ same bytes);</li>
 *   <li>{@link #delete} of a missing key is a silent no-op, so orphan cleanup can safely re-run;</li>
 *   <li>{@link #publicUrl} is a pure function of the key — it never touches the backend.</li>
 * </ul>
 */
public interface AvatarStorage {

    void put(AvatarKey key, byte[] jpeg);

    Optional<byte[]> read(AvatarKey key);

    boolean exists(AvatarKey key);

    void delete(AvatarKey key);

    URI publicUrl(AvatarKey key);
}
