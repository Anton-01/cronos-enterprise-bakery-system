package com.ninsky.cronos.application.response.quote;

import java.time.Instant;

/**
 * One entry of a quote's status history (§4.2).
 *
 * @param actorType {@code BAKER}, {@code CLIENT} or {@code SYSTEM}
 * @param detail    share channel, reject reason or {@code DUPLICATED_FROM:{number}}
 */
public record QuoteStatusChange(String status, Instant changedAt, String actorType, String actorName, String detail, String comment) {
}
