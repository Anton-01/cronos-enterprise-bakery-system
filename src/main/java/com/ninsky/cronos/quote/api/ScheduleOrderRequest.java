package com.ninsky.cronos.quote.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * Order to schedule while accepting a quote (§4.4).
 *
 * @param reminders minutes before {@code startAt}, each in {0, 15, 30, 60, 120, 1440, 2880}, at most 5
 */
public record ScheduleOrderRequest(@NotNull @Pattern(regexp = "ORDER_DELIVERY|ORDER_PICKUP") String type, @NotNull Instant startAt,
                                   @NotNull Instant endAt, boolean allDay, @Size(max = 300) String location, Instant productionStartAt,
                                   @Size(max = 5) List<Integer> reminders, @Size(max = 2000) String notes) {

    public ScheduleOrderRequest {
        reminders = reminders == null ? List.of() : List.copyOf(reminders);
    }
}
