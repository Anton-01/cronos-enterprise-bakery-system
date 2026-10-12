package com.ninsky.cronos.calendar.api;

import java.math.BigDecimal;

/**
 * Figures of the calendar header (§5.5). Orders are deliveries and pickups that are not cancelled; revenue only adds
 * orders in the default currency.
 */
public record CalendarSummary(long ordersInRange, long dueToday, long inProduction, long readyForDelivery, BigDecimal revenueInRange,
                              String currency) {
}
