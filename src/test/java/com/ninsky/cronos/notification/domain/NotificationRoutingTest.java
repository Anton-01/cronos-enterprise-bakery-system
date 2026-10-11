package com.ninsky.cronos.notification.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Preferences, locked channels and quiet hours (§6.5). */
class NotificationRoutingTest {

    private static final ZoneId ZONE = ZoneId.of("America/Mexico_City");
    /** 23:30 in Mexico City. */
    private static final Instant NIGHT = Instant.parse("2026-10-12T05:30:00Z");
    /** 12:00 in Mexico City. */
    private static final Instant NOON = Instant.parse("2026-10-11T18:00:00Z");

    @ParameterizedTest
    @CsvSource({
            "22:00, 07:00, 23:30, true", "22:00, 07:00, 06:59, true", "22:00, 07:00, 07:00, false", "22:00, 07:00, 12:00, false",
            "13:00, 15:00, 14:00, true", "13:00, 15:00, 15:00, false", "13:00, 13:00, 13:00, false"
    })
    void quietHoursMayWrapMidnight(LocalTime start, LocalTime end, LocalTime time, boolean inside) {
        assertThat(new QuietHours(true, start, end).contains(time)).isEqualTo(inside);
    }

    @Test
    void defaultsFollowTheCategoryTable() {
        RecipientSettings defaults = RecipientSettings.defaults(ZONE);
        assertThat(defaults.channelsOf(NotificationCategory.QUOTES)).containsExactlyInAnyOrder(NotificationChannel.values());
        assertThat(defaults.channelsOf(NotificationCategory.ADMIN)).containsExactlyInAnyOrder(NotificationChannel.IN_APP, NotificationChannel.PUSH);
        assertThat(defaults.channelsOf(NotificationCategory.SYSTEM)).containsExactly(NotificationChannel.IN_APP);
    }

    @Test
    void lockedChannelsCannotBeTurnedOff() {
        RecipientSettings settings = new RecipientSettings(Map.of(NotificationCategory.SECURITY, Set.of()), QuietHours.OFF, ZONE);
        assertThat(settings.channelsOf(NotificationCategory.SECURITY))
                .containsExactlyInAnyOrder(NotificationChannel.IN_APP, NotificationChannel.EMAIL);
    }

    @Test
    void quietHoursDropPushButNotCriticalOnes() {
        RecipientSettings settings = new RecipientSettings(Map.of(), new QuietHours(true, LocalTime.of(22, 0), LocalTime.of(7, 0)), ZONE);

        assertThat(NotificationRouter.route(settings, NotificationType.QUOTE_ACCEPTED, NIGHT))
                .isEqualTo(EnumSet.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL));
        assertThat(NotificationRouter.route(settings, NotificationType.QUOTE_ACCEPTED, NOON)).contains(NotificationChannel.PUSH);
        assertThat(NotificationRouter.route(settings, NotificationType.ACCOUNT_LOCKED, NIGHT)).contains(NotificationChannel.PUSH);
    }

    @Test
    void disabledChannelsAreNotRouted() {
        RecipientSettings settings = new RecipientSettings(Map.of(NotificationCategory.ORDERS, Set.of(NotificationChannel.PUSH)), QuietHours.OFF, ZONE);
        assertThat(NotificationRouter.route(settings, NotificationType.ORDER_SCHEDULED, NOON)).containsExactly(NotificationChannel.PUSH);
    }

    @Test
    void everyTypeHasACategory() {
        assertThat(NotificationType.values()).allMatch(type -> type.category() != null && type.severity() != null);
    }
}
