package com.ninsky.cronos.calendar.api;

import com.ninsky.cronos.calendar.domain.CalendarEventStatus;
import com.ninsky.cronos.calendar.domain.CalendarEventType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** {@code CalendarEvent} of the contract (§5.1). */
public record CalendarEventResponse(UUID id, String title, CalendarEventType type, CalendarEventStatus status, Instant startAt,
                                    Instant endAt, boolean allDay, String color, String location, String clientName, String clientPhone,
                                    String clientEmail, String notes, UUID quoteId, String quoteNumber, BigDecimal totalAmount,
                                    String currency, List<CalendarEventRecipeResponse> recipes, List<Integer> reminders,
                                    Instant productionStartAt, Instant createdAt, Instant updatedAt, long version) {

    public CalendarEventResponse {
        recipes = recipes == null ? List.of() : List.copyOf(recipes);
        reminders = reminders == null ? List.of() : List.copyOf(reminders);
    }
}
