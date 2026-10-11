package com.ninsky.cronos.notification.api;

import com.ninsky.cronos.notification.domain.NotificationCategory;
import com.ninsky.cronos.notification.domain.NotificationChannel;

import java.util.List;
import java.util.Map;

/** {@code NotificationSettings} of the contract (§6.5). */
public record NotificationSettingsResponse(List<CategoryPreference> preferences, QuietHoursView quietHours, int defaultReminderMinutes,
                                           String timezone, long version) {

    public NotificationSettingsResponse {
        preferences = List.copyOf(preferences);
    }

    /** Channels of one category; {@code locked} channels stay on. */
    public record CategoryPreference(NotificationCategory category, Map<NotificationChannel, Boolean> channels,
                                     List<NotificationChannel> locked) {
        public CategoryPreference {
            channels = Map.copyOf(channels);
            locked = List.copyOf(locked);
        }
    }

    /** {@code HH:mm} times in the user's zone. */
    public record QuietHoursView(boolean enabled, String start, String end) {
    }
}
