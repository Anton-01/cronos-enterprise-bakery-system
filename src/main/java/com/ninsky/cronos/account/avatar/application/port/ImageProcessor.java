package com.ninsky.cronos.account.avatar.application.port;

import com.ninsky.cronos.account.avatar.domain.ProcessedImage;

/**
 * Turns untrusted upload bytes into a safe avatar: sniff magic bytes, read dimensions from the
 * header, enforce pixel/edge limits, fully decode, then re-encode as a metadata-free JPEG.
 */
public interface ImageProcessor {

    /**
     * @throws com.ninsky.cronos.account.shared.domain.AccountDomainException carrying
     *         {@code ImageRejected} (415 unsupported type, 400 corrupt / too small / too many pixels)
     */
    ProcessedImage process(byte[] upload);
}
