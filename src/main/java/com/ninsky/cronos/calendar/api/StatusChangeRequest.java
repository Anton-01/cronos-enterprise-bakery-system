package com.ninsky.cronos.calendar.api;

import com.ninsky.cronos.calendar.domain.CalendarEventStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code POST /calendar/events/{id}/status}. */
public record StatusChangeRequest(@NotNull CalendarEventStatus status, @NotNull Long version, @Size(max = 500) String reason) {
}
