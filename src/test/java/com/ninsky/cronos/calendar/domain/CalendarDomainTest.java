package com.ninsky.cronos.calendar.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Status flow (§5.2) and reminder planning (§5.6). */
class CalendarDomainTest {

    private static final Instant START = Instant.parse("2026-10-20T18:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-18T12:00:00Z");

    @ParameterizedTest
    @CsvSource({
            "TENTATIVE, CONFIRMED, true", "TENTATIVE, CANCELLED, true", "TENTATIVE, READY, false",
            "CONFIRMED, IN_PRODUCTION, true", "CONFIRMED, READY, true", "CONFIRMED, DELIVERED, false",
            "IN_PRODUCTION, READY, true", "IN_PRODUCTION, CONFIRMED, false", "READY, DELIVERED, true",
            "CANCELLED, TENTATIVE, true", "CANCELLED, CONFIRMED, false", "DELIVERED, CANCELLED, false"
    })
    void followsTheStatusFlow(CalendarEventStatus from, CalendarEventStatus to, boolean allowed) {
        assertThat(from.canMoveTo(to)).isEqualTo(allowed);
    }

    @Test
    void onlyDeliveriesAndPickupsAreOrders() {
        assertThat(CalendarEventType.ORDER_DELIVERY.order()).isTrue();
        assertThat(CalendarEventType.ORDER_PICKUP.order()).isTrue();
        assertThat(CalendarEventType.TASTING.order()).isFalse();
    }

    @Test
    void plansRemindersAndProductionStartEarliestFirst() {
        Instant production = START.minus(Duration.ofHours(8));
        List<ReminderPlan.Planned> planned = ReminderPlan.plan(CalendarEventStatus.CONFIRMED, START, List.of(60, 1440), production, NOW);
        assertThat(planned).extracting(ReminderPlan.Planned::fireAt)
                .containsExactly(START.minus(Duration.ofDays(1)), production, START.minus(Duration.ofHours(1)));
        assertThat(planned.get(1).kind()).isEqualTo(ReminderKind.PRODUCTION_START);
        assertThat(planned.get(1).minutesBefore()).isNull();
    }

    @Test
    void dropsRemindersAlreadyPast() {
        Instant dayBefore = START.minus(Duration.ofDays(1));
        assertThat(ReminderPlan.plan(CalendarEventStatus.CONFIRMED, START, List.of(2880, 60), null, dayBefore))
                .extracting(ReminderPlan.Planned::minutesBefore).containsExactly(60);
    }

    @Test
    void inactiveEntriesHaveNoReminders() {
        assertThat(ReminderPlan.plan(CalendarEventStatus.CANCELLED, START, List.of(60), START, NOW)).isEmpty();
        assertThat(ReminderPlan.plan(CalendarEventStatus.DELIVERED, START, List.of(60), null, NOW)).isEmpty();
    }

    @Test
    void validatesOffsets() {
        assertThat(ReminderPlan.valid(List.of(0, 15, 30, 60, 120))).isTrue();
        assertThat(ReminderPlan.valid(List.of(0, 15, 30, 60, 120, 1440))).isFalse();
        assertThat(ReminderPlan.valid(List.of(45))).isFalse();
        assertThat(ReminderPlan.valid(List.of(60, 60))).isFalse();
    }

    @Test
    void dedupeKeyChangesWithTheInstantOnly() {
        UUID id = UUID.randomUUID();
        ReminderPlan.Planned a = new ReminderPlan.Planned(ReminderKind.REMINDER, 60, START);
        ReminderPlan.Planned b = new ReminderPlan.Planned(ReminderKind.REMINDER, 60, START);
        ReminderPlan.Planned moved = new ReminderPlan.Planned(ReminderKind.REMINDER, 60, START.plusSeconds(3600));
        assertThat(a.dedupeKey(id)).isEqualTo(b.dedupeKey(id)).isNotEqualTo(moved.dedupeKey(id)).hasSizeLessThanOrEqualTo(80);
    }
}
