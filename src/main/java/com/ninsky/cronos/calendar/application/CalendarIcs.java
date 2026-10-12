package com.ninsky.cronos.calendar.application;

import com.ninsky.cronos.calendar.api.CalendarEventRecipeResponse;
import com.ninsky.cronos.calendar.api.CalendarEventResponse;
import com.ninsky.cronos.calendar.domain.CalendarEventStatus;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** RFC 5545 export of calendar entries (§5.7): UTC times, all-day dates, escaped text and lines folded at 75 octets. */
public final class CalendarIcs {

    static final String CRLF = "\r\n";
    static final int FOLD_OCTETS = 75;
    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** Localised labels of the description. */
    public record Labels(String client, String phone, String recipes, String quote) {
    }

    private CalendarIcs() {
    }

    /**
     * The calendar document of {@code events}.
     *
     * @param zone tenant zone, used for the dates of all-day entries
     */
    public static String write(List<CalendarEventResponse> events, Instant now, ZoneId zone, Labels labels) {
        List<String> lines = new ArrayList<>();
        lines.add("BEGIN:VCALENDAR");
        lines.add("VERSION:2.0");
        lines.add("PRODID:-//Cronos//Calendar//ES");
        lines.add("CALSCALE:GREGORIAN");
        lines.add("METHOD:PUBLISH");
        events.forEach(event -> vevent(event, now, zone, labels, lines));
        lines.add("END:VCALENDAR");
        return lines.stream().map(CalendarIcs::fold).collect(Collectors.joining(CRLF, "", CRLF));
    }

    private static void vevent(CalendarEventResponse event, Instant now, ZoneId zone, Labels labels, List<String> lines) {
        lines.add("BEGIN:VEVENT");
        lines.add("UID:" + event.id() + "@cronos");
        lines.add("DTSTAMP:" + UTC.format(now));
        if (event.allDay()) {
            lines.add("DTSTART;VALUE=DATE:" + DATE.format(event.startAt().atZone(zone)));
            lines.add("DTEND;VALUE=DATE:" + DATE.format(event.endAt().atZone(zone)));
        } else {
            lines.add("DTSTART:" + UTC.format(event.startAt()));
            lines.add("DTEND:" + UTC.format(event.endAt()));
        }
        lines.add("SUMMARY:" + escape(event.title()));
        if (event.location() != null && !event.location().isBlank()) {
            lines.add("LOCATION:" + escape(event.location()));
        }
        String description = description(event, labels);
        if (!description.isEmpty()) {
            lines.add("DESCRIPTION:" + escape(description));
        }
        lines.add("STATUS:" + status(event.status()));
        if (event.updatedAt() != null) {
            lines.add("LAST-MODIFIED:" + UTC.format(event.updatedAt()));
        }
        if (event.status().active()) {
            event.reminders().forEach(minutes -> {
                lines.add("BEGIN:VALARM");
                lines.add("ACTION:DISPLAY");
                lines.add("DESCRIPTION:" + escape(event.title()));
                lines.add("TRIGGER:-PT" + minutes + "M");
                lines.add("END:VALARM");
            });
        }
        lines.add("END:VEVENT");
    }

    private static String description(CalendarEventResponse event, Labels labels) {
        List<String> parts = new ArrayList<>();
        if (event.clientName() != null) {
            parts.add(labels.client() + ": " + event.clientName());
        }
        if (event.clientPhone() != null) {
            parts.add(labels.phone() + ": " + event.clientPhone());
        }
        if (!event.recipes().isEmpty()) {
            parts.add(labels.recipes() + ": " + event.recipes().stream().map(CalendarIcs::recipe).collect(Collectors.joining(", ")));
        }
        if (event.quoteNumber() != null) {
            parts.add(labels.quote() + ": " + event.quoteNumber());
        }
        if (event.notes() != null && !event.notes().isBlank()) {
            parts.add(event.notes());
        }
        return String.join("\n", parts);
    }

    private static String recipe(CalendarEventRecipeResponse recipe) {
        String unit = recipe.yieldUnit() == null ? "" : " " + recipe.yieldUnit();
        return recipe.quantity().stripTrailingZeros().toPlainString() + unit + " × " + (recipe.recipeName() == null ? "?" : recipe.recipeName());
    }

    static String status(CalendarEventStatus status) {
        return switch (status) {
            case TENTATIVE -> "TENTATIVE";
            case CANCELLED -> "CANCELLED";
            default -> "CONFIRMED";
        };
    }

    /** TEXT escaping of RFC 5545 §3.3.11. */
    static String escape(String text) {
        return text.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\r\n", "\\n").replace("\n", "\\n")
                .replace("\r", "\\n");
    }

    /** Folds a content line at 75 octets without splitting a UTF-8 character; continuation lines start with a space. */
    static String fold(String line) {
        if (line.getBytes(StandardCharsets.UTF_8).length <= FOLD_OCTETS) {
            return line;
        }
        StringBuilder folded = new StringBuilder();
        int octets = 0;
        int limit = FOLD_OCTETS;
        for (int offset = 0; offset < line.length(); ) {
            int codePoint = line.codePointAt(offset);
            String character = new String(Character.toChars(codePoint));
            int size = character.getBytes(StandardCharsets.UTF_8).length;
            if (octets + size > limit) {
                folded.append(CRLF).append(' ');
                octets = 0;
                limit = FOLD_OCTETS - 1;
            }
            folded.append(character);
            octets += size;
            offset += Character.charCount(codePoint);
        }
        return folded.toString();
    }
}
