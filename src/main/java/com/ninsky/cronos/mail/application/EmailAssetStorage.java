package com.ninsky.cronos.mail.application;

import java.util.Optional;

/**
 * Object storage of email images (local filesystem in dev, GCS in prod). Keys are content-addressed
 * ({@code mail/{sha256}.{ext}}), so {@link #put} is idempotent and objects are immutable. Served anonymously under
 * {@code app.mail.assets.public-base-url}: mail clients and image proxies fetch without cookies or tokens.
 */
public interface EmailAssetStorage {

    void put(String key, byte[] bytes, String contentType);

    Optional<byte[]> read(String key);

    /** Missing keys are a silent no-op. */
    void delete(String key);
}
