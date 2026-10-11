package com.ninsky.cronos.quote.api;

import java.util.UUID;

/** Result of accepting; {@code calendarEventId} is null when nothing was scheduled. */
public record AcceptQuoteResponse(UUID quoteId, String status, UUID calendarEventId) {
}
