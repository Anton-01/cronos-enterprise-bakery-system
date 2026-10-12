package com.ninsky.cronos.notification.domain;

import java.time.LocalTime;

/**
 * Daily window without pushes, in the user's zone; it may wrap midnight (22:00–07:00). {@code start == end} is an
 * empty window.
 */
public record QuietHours(boolean enabled, LocalTime start, LocalTime end) {

    public static final QuietHours OFF = new QuietHours(false, null, null);

    public QuietHours {
        if (enabled && (start == null || end == null)) {
            throw new IllegalArgumentException("Enabled quiet hours need a start and an end");
        }
    }

    /** True when {@code time} falls inside the window (start inclusive, end exclusive). */
    public boolean contains(LocalTime time) {
        if (!enabled || start.equals(end)) {
            return false;
        }
        return start.isBefore(end)
                ? !time.isBefore(start) && time.isBefore(end)
                : !time.isBefore(start) || time.isBefore(end);
    }
}
