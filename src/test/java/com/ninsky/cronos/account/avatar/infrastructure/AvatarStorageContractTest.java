package com.ninsky.cronos.account.avatar.infrastructure;

import com.ninsky.cronos.account.avatar.application.port.AvatarStorage;
import com.ninsky.cronos.account.avatar.domain.AvatarKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * The one contract every {@link AvatarStorage} adapter must honour (Liskov): subclasses only supply
 * the adapter. Add a subclass per adapter (a GCS one would run against fake-gcs-server).
 */
abstract class AvatarStorageContractTest {

    private static final UUID USER = UUID.randomUUID();
    private static final byte[] CONTENT = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 42};

    protected AvatarStorage storage;
    private AvatarKey key;

    protected abstract AvatarStorage createStorage() throws Exception;

    @BeforeEach
    void setUp() throws Exception {
        storage = createStorage();
        key = AvatarKey.forContent(USER, CONTENT);
    }

    @Test
    void putThenReadReturnsTheSameBytes() {
        storage.put(key, CONTENT);

        assertThat(storage.exists(key)).isTrue();
        assertThat(storage.read(key)).hasValueSatisfying(bytes -> assertThat(bytes).containsExactly(CONTENT));
    }

    @Test
    void putIsIdempotentForTheSameContentAddressedKey() {
        storage.put(key, CONTENT);
        storage.put(key, CONTENT);

        assertThat(storage.read(key)).hasValueSatisfying(bytes -> assertThat(bytes).containsExactly(CONTENT));
    }

    @Test
    void readOfMissingObjectIsEmpty() {
        assertThat(storage.read(key)).isEmpty();
        assertThat(storage.exists(key)).isFalse();
    }

    @Test
    void deleteIsIdempotentSoOrphanCleanupCanRetry() {
        storage.put(key, CONTENT);

        storage.delete(key);
        assertThatCode(() -> storage.delete(key)).doesNotThrowAnyException();

        assertThat(storage.exists(key)).isFalse();
    }

    @Test
    void publicUrlIsAPureFunctionOfTheKeyAndChangesWithContent() {
        AvatarKey other = AvatarKey.forContent(USER, new byte[]{1});

        assertThat(storage.publicUrl(key)).isEqualTo(storage.publicUrl(key));
        assertThat(storage.publicUrl(key)).isNotEqualTo(storage.publicUrl(other));
        assertThat(storage.publicUrl(key).toString()).endsWith(key.value());
    }
}
