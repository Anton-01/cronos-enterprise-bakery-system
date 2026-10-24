package com.ninsky.cronos.mail.api;

import java.util.UUID;

/**
 * An uploaded email image.
 *
 * @param url absolute, public, never expires (emails sit in inboxes for years)
 */
public record EmailAssetResponse(UUID id, String url, String contentType, int sizeBytes, Integer width, Integer height) {
}
