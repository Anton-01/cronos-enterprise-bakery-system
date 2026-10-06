package com.ninsky.cronos.iam.twofactor;

/** Renders a QR code on our own servers; third-party QR URLs would leak the secret. */
public interface QrCodeRenderer {

    /** {@code data:image/png;base64,…} */
    String pngDataUri(String content);
}
