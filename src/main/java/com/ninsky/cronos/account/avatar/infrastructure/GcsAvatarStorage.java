package com.ninsky.cronos.account.avatar.infrastructure;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.ninsky.cronos.account.avatar.application.port.AvatarStorage;
import com.ninsky.cronos.account.avatar.domain.AvatarKey;

import java.net.URI;
import java.util.Optional;
import java.util.function.Function;

/**
 * Production adapter on Google Cloud Storage (the storage backend this codebase already uses — see
 * {@code GcsStorageAdapter}). Objects are immutable (content-addressed), so they are written with a
 * one-year {@code immutable} Cache-Control for the CDN in front of the bucket.
 */
public class GcsAvatarStorage implements AvatarStorage {

    static final String CACHE_CONTROL = "public, max-age=31536000, immutable";

    private final Storage storage;
    private final String bucket;
    private final Function<AvatarKey, URI> publicUrls;

    public GcsAvatarStorage(Storage storage, String bucket, Function<AvatarKey, URI> publicUrls) {
        this.storage = storage;
        this.bucket = bucket;
        this.publicUrls = publicUrls;
    }

    @Override
    public void put(AvatarKey key, byte[] jpeg) {
        BlobInfo info = BlobInfo.newBuilder(BlobId.of(bucket, key.value()))
                .setContentType("image/jpeg")
                .setCacheControl(CACHE_CONTROL)
                .setContentDisposition("inline")
                .build();
        storage.create(info, jpeg);
    }

    @Override
    public Optional<byte[]> read(AvatarKey key) {
        Blob blob = storage.get(BlobId.of(bucket, key.value()));
        return blob == null ? Optional.empty() : Optional.of(blob.getContent());
    }

    @Override
    public boolean exists(AvatarKey key) {
        return storage.get(BlobId.of(bucket, key.value())) != null;
    }

    /** {@code Storage#delete} returns false for a missing object — already idempotent. */
    @Override
    public void delete(AvatarKey key) {
        storage.delete(BlobId.of(bucket, key.value()));
    }

    @Override
    public URI publicUrl(AvatarKey key) {
        return publicUrls.apply(key);
    }
}
