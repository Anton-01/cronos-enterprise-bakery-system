package com.ninsky.cronos.eventing.application;

import java.time.Instant;

/**
 * Optional delivery hints of one publication.
 *
 * @param dedupeKey   coalescing key: a second publication with the same key is dropped while the first is
 *                    kept (e.g. {@code quote-viewed:{id}:{6h-bucket}}); null to always publish
 * @param availableAt earliest dispatch time, null for "as soon as the transaction commits"
 */
public record PublishOptions(String dedupeKey, Instant availableAt) {

    /** No hints. */
    public static final PublishOptions NONE = new PublishOptions(null, null);

    /** Coalesce on {@code key}. */
    public static PublishOptions dedupe(String key) {
        return new PublishOptions(key, null);
    }

    /** Dispatch not before {@code instant}. */
    public static PublishOptions notBefore(Instant instant) {
        return new PublishOptions(null, instant);
    }
}
