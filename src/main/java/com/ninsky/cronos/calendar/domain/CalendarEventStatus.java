package com.ninsky.cronos.calendar.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Status of a calendar entry and its flow (§5.2):
 * <pre>
 * TENTATIVE     → CONFIRMED | CANCELLED
 * CONFIRMED     → IN_PRODUCTION | READY | CANCELLED
 * IN_PRODUCTION → READY | CANCELLED
 * READY         → DELIVERED | CANCELLED
 * CANCELLED     → TENTATIVE
 * DELIVERED     (final)
 * </pre>
 */
public enum CalendarEventStatus {
    TENTATIVE, CONFIRMED, IN_PRODUCTION, READY, DELIVERED, CANCELLED;

    private static final Map<CalendarEventStatus, Set<CalendarEventStatus>> FLOW;

    static {
        Map<CalendarEventStatus, Set<CalendarEventStatus>> flow = new EnumMap<>(CalendarEventStatus.class);
        flow.put(TENTATIVE, EnumSet.of(CONFIRMED, CANCELLED));
        flow.put(CONFIRMED, EnumSet.of(IN_PRODUCTION, READY, CANCELLED));
        flow.put(IN_PRODUCTION, EnumSet.of(READY, CANCELLED));
        flow.put(READY, EnumSet.of(DELIVERED, CANCELLED));
        flow.put(DELIVERED, EnumSet.noneOf(CalendarEventStatus.class));
        flow.put(CANCELLED, EnumSet.of(TENTATIVE));
        flow.replaceAll((status, targets) -> Collections.unmodifiableSet(targets));
        FLOW = Collections.unmodifiableMap(flow);
    }

    public boolean canMoveTo(CalendarEventStatus target) {
        return FLOW.get(this).contains(target);
    }

    /** The statuses reachable from this one. */
    public Set<CalendarEventStatus> next() {
        return FLOW.get(this);
    }

    /** Still to happen: reminders apply and it counts as upcoming. */
    public boolean active() {
        return this != CANCELLED && this != DELIVERED;
    }

    /** Statuses a new entry may start in. */
    public boolean initial() {
        return this == TENTATIVE || this == CONFIRMED;
    }
}
