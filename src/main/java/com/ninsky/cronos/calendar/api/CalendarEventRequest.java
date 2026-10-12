package com.ninsky.cronos.calendar.api;

import com.ninsky.cronos.calendar.domain.CalendarEventStatus;
import com.ninsky.cronos.calendar.domain.CalendarEventType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Body of {@code POST /calendar/events} and {@code PUT /calendar/events/{id}}. On create {@code status} may be TENTATIVE
 * (default) or CONFIRMED and {@code quoteId} links an ACCEPTED quote of the caller; on update both are ignored (use
 * {@code /status}) and {@code version} is required. All-day entries run from midnight to midnight (tenant zone, end
 * exclusive).
 */
public record CalendarEventRequest(@NotBlank @Size(max = 150) String title,
                                   @NotNull CalendarEventType type,
                                   CalendarEventStatus status,
                                   @NotNull Instant startAt,
                                   @NotNull Instant endAt,
                                   boolean allDay,
                                   @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String color,
                                   @Size(max = 300) String location,
                                   @Size(max = 150) String clientName,
                                   @Size(max = 20) String clientPhone,
                                   @Email @Size(max = 254) String clientEmail,
                                   @Size(max = 2000) String notes,
                                   UUID quoteId,
                                   @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal totalAmount,
                                   @Pattern(regexp = "^[A-Za-z]{3}$") String currency,
                                   @Valid @Size(max = 20) List<@NotNull CalendarEventRecipeRequest> recipes,
                                   @Size(max = 5) List<Integer> reminders,
                                   Instant productionStartAt,
                                   Long version) {

    public CalendarEventRequest {
        recipes = recipes == null ? List.of() : List.copyOf(recipes);
        reminders = reminders == null ? List.of() : List.copyOf(reminders);
    }
}
