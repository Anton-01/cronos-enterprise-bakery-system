package com.ninsky.cronos.infrastructure.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Object storage abstraction, consumed by Recipe files, Recipe shares (signed URLs), and Quote
 * item image snapshots. One interface, one real adapter ({@link GcsStorageAdapter}) — unlike
 * {@code KmsPort}'s multi-provider setup, there's only one real storage backend today, so no
 * factory/profile-switching indirection is warranted here.
 */
public interface StoragePort {

    /** Uploads {@code file} into a virtual {@code folder} (e.g. "recipes/{uuid}"); returns the stored file path. */
    String uploadFile(MultipartFile file, String folder) throws IOException;

    /** Stores {@code bytes} under an exact, caller-chosen {@code key}. */
    void put(String key, byte[] bytes, String contentType);

    /** Server-side copy of {@code sourceKey} to {@code targetKey} (no download). */
    void copy(String sourceKey, String targetKey);

    boolean deleteFile(String filePath);

    /** A temporary, auto-expiring URL for viewing a stored file. */
    String generateSignedUrl(String filePath, int expirationMinutes);
}
