package com.ninsky.cronos.account.avatar.domain;

import com.ninsky.cronos.account.shared.domain.DomainValidationException;

public record ImageDimensions(int width, int height) {

    public ImageDimensions {
        if (width <= 0 || height <= 0) {
            throw new DomainValidationException("account.avatar.file.corrupt");
        }
    }

    public long pixels() {
        return (long) width * height;
    }

    public int shorterEdge() {
        return Math.min(width, height);
    }

    public boolean exceedsPixels(long maxPixels) {
        return pixels() > maxPixels;
    }
}
