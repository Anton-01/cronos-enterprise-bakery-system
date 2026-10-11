package com.ninsky.cronos.calendar.api;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/** {@code PATCH /calendar/events/{id}/schedule} (drag-to-reschedule). */
public record RescheduleRequest(@NotNull Instant startAt, @NotNull Instant endAt, @NotNull Long version) {
}
