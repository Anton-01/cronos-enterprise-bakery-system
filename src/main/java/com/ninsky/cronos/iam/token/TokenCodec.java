package com.ninsky.cronos.iam.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;

/** Raw token generation (32 random bytes, URL-safe Base64) and its SHA-256 hex digest, the only stored form. */
public final class TokenCodec {

    public static final int BYTES = 32;
    private static final Pattern SHAPE = Pattern.compile("^[A-Za-z0-9_-]{43}$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private TokenCodec() {
    }

    public static String generate() {
        byte[] bytes = new byte[BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Cheap shape check before touching the database. */
    public static boolean wellFormed(String raw) {
        return raw != null && SHAPE.matcher(raw).matches();
    }

    public static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
