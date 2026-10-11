package com.ninsky.cronos.quote.application;

import com.ninsky.cronos.domain.model.quote.Quote;
import com.ninsky.cronos.quote.api.ScheduleOrderRequest;

import java.util.UUID;

/**
 * The calendar module's application API as the quote module needs it: creates the order of an accepted quote inside
 * the caller's transaction (a failure rolls the acceptance back).
 */
public interface OrderScheduling {

    /**
     * Creates a CONFIRMED order event for {@code quote} owned by {@code ownerId}.
     *
     * @return the calendar event id
     * @throws com.ninsky.cronos.infrastructure.exception.ApiException VALIDATION_ERROR on {@code schedule.*}
     */
    UUID scheduleFromQuote(UUID ownerId, Quote quote, ScheduleOrderRequest schedule);
}
