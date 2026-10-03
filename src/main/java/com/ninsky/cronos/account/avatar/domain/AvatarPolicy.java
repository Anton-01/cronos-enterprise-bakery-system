package com.ninsky.cronos.account.avatar.domain;

/** Server-side avatar limits. The client's crop/resize is never trusted: all of this is re-checked here. */
public final class AvatarPolicy {

    public static final long MAX_UPLOAD_BYTES = 2L * 1024 * 1024;
    /** Decompression-bomb guard, checked from the header BEFORE any pixel is decoded. */
    public static final long MAX_SOURCE_PIXELS = 40_000_000L;
    public static final int MIN_EDGE_PX = 128;
    public static final int OUTPUT_MAX_EDGE_PX = 512;
    public static final float JPEG_QUALITY = 0.9f;

    private AvatarPolicy() {
    }
}
