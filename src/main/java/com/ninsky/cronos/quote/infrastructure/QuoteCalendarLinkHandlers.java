package com.ninsky.cronos.quote.infrastructure;

import com.ninsky.cronos.calendar.domain.CalendarEvents;
import com.ninsky.cronos.eventing.application.OutboxHandler;
import com.ninsky.cronos.eventing.application.OutboxHandlers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Keeps {@code quotes.calendar_event_id} in step with the calendar: an entry created for an accepted quote links it
 * (when it has no order yet) and a deleted entry unlinks it. Both updates are conditional, so they are idempotent, and
 * bump the quote's version so a concurrent edit of the quote cannot write the old link back.
 */
@Configuration
public class QuoteCalendarLinkHandlers {

    private final NamedParameterJdbcTemplate jdbc;

    public QuoteCalendarLinkHandlers(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Bean
    OutboxHandler<CalendarEvents.CalendarEventCreated> linkQuoteToCalendar() {
        return OutboxHandlers.of(CalendarEvents.CalendarEventCreated.class, "QuoteCalendarLink", (event, envelope) -> {
            if (event.quoteId() != null) {
                jdbc.update("""
                                UPDATE quotes SET calendar_event_id = :event, version = coalesce(version, 0) + 1
                                WHERE id = :quote AND user_id = :owner AND calendar_event_id IS NULL""",
                        new MapSqlParameterSource("event", event.eventId()).addValue("quote", event.quoteId())
                                .addValue("owner", event.ownerId()));
            }
        });
    }

    @Bean
    OutboxHandler<CalendarEvents.CalendarEventDeleted> unlinkQuoteFromCalendar() {
        return OutboxHandlers.of(CalendarEvents.CalendarEventDeleted.class, "QuoteCalendarUnlink", (event, envelope) ->
                jdbc.update("UPDATE quotes SET calendar_event_id = NULL, version = coalesce(version, 0) + 1 WHERE calendar_event_id = :event",
                        new MapSqlParameterSource("event", event.eventId())));
    }
}
