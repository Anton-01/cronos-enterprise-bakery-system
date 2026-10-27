package com.ninsky.cronos.mail.infrastructure;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.ninsky.cronos.mail.application.EmailAssetStorage;

import java.util.Optional;

/**
 * Production adapter on Google Cloud Storage (the object store this codebase uses), behind the public CDN of
 * {@code app.mail.assets.public-base-url}. Objects are immutable, so they carry a one-year {@code immutable} Cache-Control.
 */
public class GcsEmailAssetStorage implements EmailAssetStorage {

    static final String CACHE_CONTROL = "public, max-age=31536000, immutable";

    private final Storage storage;
    private final String bucket;

    public GcsEmailAssetStorage(Storage storage, String bucket) {
        this.storage = storage;
        this.bucket = bucket;
    }

    @Override
    public void put(String key, byte[] bytes, String contentType) {
        storage.create(BlobInfo.newBuilder(BlobId.of(bucket, key)).setContentType(contentType).setCacheControl(CACHE_CONTROL)
                .setContentDisposition("inline").build(), bytes);
    }

    @Override
    public Optional<byte[]> read(String key) {
        Blob blob = storage.get(BlobId.of(bucket, key));
        return blob == null ? Optional.empty() : Optional.of(blob.getContent());
    }

    @Override
    public void delete(String key) {
        storage.delete(BlobId.of(bucket, key));
    }
}
