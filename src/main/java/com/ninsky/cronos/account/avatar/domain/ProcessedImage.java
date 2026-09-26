package com.ninsky.cronos.account.avatar.domain;

import java.util.Arrays;
import java.util.Objects;

/** Output of the image pipeline: a freshly re-encoded, metadata-free JPEG. */
public record ProcessedImage(byte[] jpeg, ImageDimensions dimensions, DetectedImageType sourceType) {

    public ProcessedImage {
        Objects.requireNonNull(jpeg, "jpeg");
        Objects.requireNonNull(dimensions, "dimensions");
        Objects.requireNonNull(sourceType, "sourceType");
        jpeg = jpeg.clone();
    }

    @Override
    public byte[] jpeg() {
        return jpeg.clone();
    }

    public int size() {
        return jpeg.length;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ProcessedImage other
                && Arrays.equals(jpeg, other.jpeg)
                && dimensions.equals(other.dimensions)
                && sourceType.equals(other.sourceType);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(jpeg) + Objects.hash(dimensions, sourceType);
    }

    /** Never print image bytes. */
    @Override
    public String toString() {
        return "ProcessedImage[" + dimensions.width() + "x" + dimensions.height() + ", " + jpeg.length + " bytes]";
    }
}
