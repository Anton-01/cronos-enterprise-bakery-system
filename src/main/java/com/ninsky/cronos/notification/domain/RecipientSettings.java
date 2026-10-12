package com.ninsky.cronos.notification.domain;

import java.time.ZoneId;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** What the router needs about a recipient: enabled channels per category, quiet hours and zone. */
public record RecipientSettings(Map<NotificationCategory, Set<NotificationChannel>> channels, QuietHours quietHours, ZoneId zone) {

    public RecipientSettings {
        EnumMap<NotificationCategory, Set<NotificationChannel>> copy = new EnumMap<>(NotificationCategory.class);
        for (NotificationCategory category : NotificationCategory.values()) {
            Set<NotificationChannel> requested = channels.getOrDefault(category, category.defaults());
            copy.put(category, Collections.unmodifiableSet(category.effective(requested)));
        }
        channels = Collections.unmodifiableMap(copy);
        quietHours = quietHours == null ? QuietHours.OFF : quietHours;
    }

    /** Defaults of a user who never opened the settings. */
    public static RecipientSettings defaults(ZoneId zone) {
        return new RecipientSettings(Map.of(), QuietHours.OFF, zone);
    }

    public Set<NotificationChannel> channelsOf(NotificationCategory category) {
        return channels.getOrDefault(category, EnumSet.noneOf(NotificationChannel.class));
    }
}
