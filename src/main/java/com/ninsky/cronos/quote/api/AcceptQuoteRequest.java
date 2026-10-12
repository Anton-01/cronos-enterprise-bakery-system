package com.ninsky.cronos.quote.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/** {@code POST /quotes/{id}/accept}: accept and, optionally, schedule the order in the same transaction. */
public record AcceptQuoteRequest(@Valid ScheduleOrderRequest schedule, @Size(max = 500) String comment) {
}
