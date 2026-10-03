package com.ninsky.cronos.account.avatar.domain;

import com.ninsky.cronos.account.shared.domain.DomainValidationException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Content-addressed object key {@code avatars/{userId}/{sha256[0..16]}.jpg}. The hash is over the
 * re-encoded JPEG, so a new image always means a new key (and a new public URL, defeating stale
 * browser/CDN caches) while re-uploading the identical image is a no-op.
 */
public record AvatarKey(String value) {

    private static final Pattern FORMAT = Pattern.compile(
            "^avatars/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/[0-9a-f]{16}\\.jpg$");
    private static final int HASH_HEX_CHARS = 16;

    public AvatarKey {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new DomainValidationException("account.avatar.key.invalid");
        }
    }

    public static AvatarKey forContent(UUID userId, byte[] jpeg) {
        return new AvatarKey("avatars/" + userId + "/" + sha256Hex(jpeg).substring(0, HASH_HEX_CHARS) + ".jpg");
    }

    /** Rebuilds a key from the public path parts; throws for anything that isn't a well-formed key. */
    public static AvatarKey of(UUID userId, String fileName) {
        return new AvatarKey("avatars/" + userId + "/" + fileName);
    }

    public static AvatarKey ofNullable(String value) {
        return value == null ? null : new AvatarKey(value);
    }

    public UUID userId() {
        return UUID.fromString(value.substring("avatars/".length(), "avatars/".length() + 36));
    }

    public String fileName() {
        return value.substring(value.lastIndexOf('/') + 1);
    }

    public boolean belongsTo(UUID userId) {
        return userId().equals(userId);
    }

    private static String sha256Hex(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
