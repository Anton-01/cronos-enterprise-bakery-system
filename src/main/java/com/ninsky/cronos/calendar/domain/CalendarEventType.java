package com.ninsky.cronos.calendar.domain;

import java.util.EnumSet;
import java.util.Set;

/** Kind of calendar entry; deliveries and pickups are the orders counted by the summary. */
public enum CalendarEventType {
    ORDER_DELIVERY, ORDER_PICKUP, PRODUCTION, TASTING, REMINDER, OTHER;

    /** The order types. */
    public static final Set<CalendarEventType> ORDERS = Set.copyOf(EnumSet.of(ORDER_DELIVERY, ORDER_PICKUP));

    public boolean order() {
        return ORDERS.contains(this);
    }
}
