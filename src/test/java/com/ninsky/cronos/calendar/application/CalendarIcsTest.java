package com.ninsky.cronos.calendar.application;

import com.ninsky.cronos.calendar.api.CalendarEventRecipeResponse;
import com.ninsky.cronos.calendar.api.CalendarEventResponse;
import com.ninsky.cronos.calendar.domain.CalendarEventStatus;
import com.ninsky.cronos.calendar.domain.CalendarEventType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** RFC 5545 export (§5.7). */
class CalendarIcsTest {

    private static final ZoneId ZONE = ZoneId.of("America/Mexico_City");
    private static final CalendarIcs.Labels LABELS = new CalendarIcs.Labels("Cliente", "Teléfono", "Recetas", "Cotización");
    private static final Instant NOW = Instant.parse("2026-10-11T15:00:00Z");

    @Test
    void writesTimedEntriesInUtcWithAlarms() {
        UUID id = UUID.randomUUID();
        String ics = CalendarIcs.write(List.of(entry(id, false, CalendarEventStatus.CONFIRMED, Instant.parse("2026-10-20T18:00:00Z"),
                Instant.parse("2026-10-20T19:00:00Z"))), NOW, ZONE, LABELS);

        assertThat(ics).startsWith("BEGIN:VCALENDAR\r\n").endsWith("END:VCALENDAR\r\n")
                .contains("PRODID:-//Cronos//Calendar//ES", "UID:" + id + "@cronos", "DTSTAMP:20261011T150000Z",
                        "DTSTART:20261020T180000Z", "DTEND:20261020T190000Z", "STATUS:CONFIRMED", "TRIGGER:-PT60M",
                        "SUMMARY:CR-AB12 · Ana\\, boda\\; 3 pisos");
    }

    @Test
    void writesAllDayEntriesAsLocalDates() {
        String ics = CalendarIcs.write(List.of(entry(UUID.randomUUID(), true, CalendarEventStatus.TENTATIVE,
                Instant.parse("2026-10-20T06:00:00Z"), Instant.parse("2026-10-21T06:00:00Z"))), NOW, ZONE, LABELS);

        assertThat(ics).contains("DTSTART;VALUE=DATE:20261020", "DTEND;VALUE=DATE:20261021", "STATUS:TENTATIVE");
    }

    @Test
    void cancelledEntriesHaveNoAlarms() {
        String ics = CalendarIcs.write(List.of(entry(UUID.randomUUID(), false, CalendarEventStatus.CANCELLED,
                Instant.parse("2026-10-20T18:00:00Z"), Instant.parse("2026-10-20T19:00:00Z"))), NOW, ZONE, LABELS);

        assertThat(ics).contains("STATUS:CANCELLED").doesNotContain("VALARM");
    }

    @Test
    void foldsLongLinesAt75OctetsWithoutSplittingCharacters() {
        String line = "DESCRIPTION:" + "ñ".repeat(80);
        String folded = CalendarIcs.fold(line);

        assertThat(Arrays.stream(folded.split("\r\n")).mapToInt(part -> part.getBytes(StandardCharsets.UTF_8).length))
                .allMatch(octets -> octets <= 75);
        assertThat(folded.replace("\r\n ", "")).isEqualTo(line);
    }

    @Test
    void escapesText() {
        assertThat(CalendarIcs.escape("a\\b;c,d\ne")).isEqualTo("a\\\\b\\;c\\,d\\ne");
    }

    private static CalendarEventResponse entry(UUID id, boolean allDay, CalendarEventStatus status, Instant start, Instant end) {
        return new CalendarEventResponse(id, "CR-AB12 · Ana, boda; 3 pisos", CalendarEventType.ORDER_DELIVERY, status, start, end, allDay, null,
                "Salón Jardín", "Ana", "5512345678", null, "Sin nueces", null, "CR-AB12", BigDecimal.TEN, "MXN",
                List.of(new CalendarEventRecipeResponse(UUID.randomUUID(), UUID.randomUUID(), "Pastel de vainilla", null, new BigDecimal("2.00"),
                        "pieza", null, List.of(), null)),
                List.of(60), null, NOW, null, 0);
    }
}
