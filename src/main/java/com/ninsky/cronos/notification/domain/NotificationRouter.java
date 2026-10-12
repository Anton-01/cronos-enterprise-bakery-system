package com.ninsky.cronos.notification.domain;

import java.time.Instant;
import java.util.EnumSet;

/**
 * Channels of one notification (§6.5): the recipient's enabled (and locked) channels of the type's category; quiet
 * hours remove PUSH unless the notification is CRITICAL. Transactional emails do not ask the router.
 */
public final class NotificationRouter {

    private NotificationRouter() {
    }

    public static EnumSet<NotificationChannel> route(RecipientSettings settings, NotificationType type, Instant now) {
        EnumSet<NotificationChannel> channels = EnumSet.noneOf(NotificationChannel.class);
        channels.addAll(settings.channelsOf(type.category()));
        if (type.severity() != NotificationSeverity.CRITICAL && settings.quietHours().contains(now.atZone(settings.zone()).toLocalTime())) {
            channels.remove(NotificationChannel.PUSH);
        }
        return channels;
    }
}
