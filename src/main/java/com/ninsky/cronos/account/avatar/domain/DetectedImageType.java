package com.ninsky.cronos.account.avatar.domain;

import java.util.Optional;
import java.util.Set;

/**
 * Image type as proven by the file's magic bytes — never by {@code Content-Type} or filename,
 * both of which are client-controlled.
 */
public sealed interface DetectedImageType permits DetectedImageType.Jpeg, DetectedImageType.Png, DetectedImageType.Webp {

    /** Bytes needed to identify every accepted type (WebP: "RIFF" + 4-byte size + "WEBP"). */
    int SNIFF_LENGTH = 12;

    Set<DetectedImageType> ACCEPTED = Set.of(new Jpeg(), new Png(), new Webp());

    String mediaType();

    /** ImageIO format name used to look up a reader. */
    String formatName();

    static Optional<DetectedImageType> sniff(byte[] head) {
        if (head == null) {
            return Optional.empty();
        }
        if (startsWith(head, 0, 0xFF, 0xD8, 0xFF)) {
            return Optional.of(new Jpeg());
        }
        if (startsWith(head, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of(new Png());
        }
        if (startsWith(head, 0, 'R', 'I', 'F', 'F') && startsWith(head, 8, 'W', 'E', 'B', 'P')) {
            return Optional.of(new Webp());
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] data, int offset, int... signature) {
        if (data.length < offset + signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((data[offset + i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }

    record Jpeg() implements DetectedImageType {
        public String mediaType() { return "image/jpeg"; }
        public String formatName() { return "jpeg"; }
    }

    record Png() implements DetectedImageType {
        public String mediaType() { return "image/png"; }
        public String formatName() { return "png"; }
    }

    record Webp() implements DetectedImageType {
        public String mediaType() { return "image/webp"; }
        public String formatName() { return "webp"; }
    }
}
