package com.ninsky.cronos.notification.api;

import com.ninsky.cronos.notification.domain.NotificationCategory;
import com.ninsky.cronos.notification.domain.NotificationChannel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/** {@code PUT /notifications/settings}; locked channels ignore writes. */
public record NotificationSettingsRequest(@NotNull @Valid @Size(max = 7) List<@NotNull @Valid Preference> preferences,
                                          @Valid QuietHoursRequest quietHours,
                                          @Min(0) @Max(10080) int defaultReminderMinutes,
                                          @NotBlank @Size(max = 64) String timezone,
                                          @NotNull Long version) {

    public NotificationSettingsRequest {
        preferences = preferences == null ? List.of() : List.copyOf(preferences);
    }

    /** Channel switches of one category. */
    public record Preference(@NotNull NotificationCategory category, @NotNull Map<NotificationChannel, Boolean> channels) {
        public Preference {
            channels = channels == null ? Map.of() : Map.copyOf(channels);
        }
    }

    /** Quiet hours as {@code HH:mm}. */
    public record QuietHoursRequest(boolean enabled,
                                    @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$") String start,
                                    @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$") String end) {
    }
}
