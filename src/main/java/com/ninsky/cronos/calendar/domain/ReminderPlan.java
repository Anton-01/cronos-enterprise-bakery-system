package com.ninsky.cronos.calendar.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/** Which reminders an entry has (§5.6): one per offset before the start plus the production start; none once inactive. */
public final class ReminderPlan {

    /** Allowed offsets in minutes before the start. */
    public static final Set<Integer> OFFSETS = Set.of(0, 15, 30, 60, 120, 1440, 2880);
    public static final int MAX_REMINDERS = 5;

    private ReminderPlan() {
    }

    /** A reminder to materialise. {@code minutesBefore} is null for the production start. */
    public record Planned(ReminderKind kind, Integer minutesBefore, Instant fireAt) {

        /** Stable per entry, kind and instant: a rescheduled reminder is a new one, an unchanged one is not sent twice. */
        public String dedupeKey(UUID eventId) {
            return eventId + ":" + kind.name().charAt(0) + ":" + fireAt.getEpochSecond();
        }
    }

    /** True when {@code offsets} are allowed, distinct and at most {@link #MAX_REMINDERS}. */
    public static boolean valid(Collection<Integer> offsets) {
        return offsets.size() <= MAX_REMINDERS && offsets.stream().allMatch(offset -> offset != null && OFFSETS.contains(offset))
                && Set.copyOf(offsets).size() == offsets.size();
    }

    /**
     * The reminders still ahead of {@code now}, earliest first.
     *
     * @param offsets minutes before {@code startAt}
     */
    public static List<Planned> plan(CalendarEventStatus status, Instant startAt, Collection<Integer> offsets, Instant productionStartAt,
                                     Instant now) {
        if (!status.active()) {
            return List.of();
        }
        List<Planned> planned = new ArrayList<>();
        new TreeSet<>(offsets.stream().filter(Objects::nonNull).toList()).forEach(minutes ->
                planned.add(new Planned(ReminderKind.REMINDER, minutes, startAt.minus(Duration.ofMinutes(minutes)))));
        if (productionStartAt != null) {
            planned.add(new Planned(ReminderKind.PRODUCTION_START, null, productionStartAt));
        }
        return planned.stream().filter(reminder -> reminder.fireAt().isAfter(now))
                .sorted(Comparator.comparing(Planned::fireAt)).toList();
    }
}
