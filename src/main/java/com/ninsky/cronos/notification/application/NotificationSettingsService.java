package com.ninsky.cronos.notification.application;

import com.ninsky.cronos.iam.shared.ActorProvider;
import com.ninsky.cronos.iam.shared.TenantTime;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.notification.api.NotificationSettingsRequest;
import com.ninsky.cronos.notification.api.NotificationSettingsResponse;
import com.ninsky.cronos.notification.domain.NotificationCategory;
import com.ninsky.cronos.notification.domain.NotificationChannel;
import com.ninsky.cronos.notification.domain.QuietHours;
import com.ninsky.cronos.notification.domain.RecipientSettings;
import com.ninsky.cronos.notification.infrastructure.NotificationSettingsCustomRepository;
import com.ninsky.cronos.notification.infrastructure.NotificationSettingsCustomRepository.SettingsRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Notification preferences of the caller (§6.5), seeded lazily with the category defaults. */
@Service
public class NotificationSettingsService {

    private final NotificationSettingsCustomRepository settings;
    private final ActorProvider actors;

    public NotificationSettingsService(NotificationSettingsCustomRepository settings, ActorProvider actors) {
        this.settings = settings;
        this.actors = actors;
    }

    @Transactional
    public NotificationSettingsResponse get() {
        UUID user = actors.require().id();
        settings.seed(user);
        return view(user);
    }

    /**
     * Writes the caller's settings; locked channels stay on and categories not sent keep their channels.
     *
     * @throws ApiException VALIDATION_ERROR on {@code timezone} or {@code quietHours}; CONCURRENT_MODIFICATION
     */
    @Transactional
    public NotificationSettingsResponse update(NotificationSettingsRequest request) {
        UUID user = actors.require().id();
        ZoneId zone = zone(request.timezone());
        QuietHours quiet = quietHours(request.quietHours());
        settings.seed(user);
        Map<NotificationCategory, Set<NotificationChannel>> channels = new EnumMap<>(recipient(user).channels());
        request.preferences().forEach(preference -> channels.put(preference.category(), preference.category().effective(
                preference.channels().entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey)
                        .collect(Collectors.toCollection(() -> EnumSet.noneOf(NotificationChannel.class))))));
        boolean written = settings.update(new SettingsRow(user, quiet.enabled(), quiet.start(), quiet.end(), request.defaultReminderMinutes(),
                zone.getId(), request.version()), request.version());
        if (!written) {
            throw ApiException.concurrentModification();
        }
        settings.replacePreferences(user, channels);
        return view(user);
    }

    /** Routing settings of {@code userIds} (defaults for users who never opened the settings). */
    @Transactional(readOnly = true)
    public Map<UUID, RecipientSettings> recipients(Collection<UUID> userIds) {
        Map<UUID, SettingsRow> rows = settings.settings(userIds);
        Map<UUID, Map<NotificationCategory, Set<NotificationChannel>>> preferences = settings.preferences(userIds);
        return userIds.stream().distinct().collect(Collectors.toMap(Function.identity(), user -> {
            SettingsRow row = rows.get(user);
            QuietHours quiet = row == null || !row.quietEnabled() || row.quietStart() == null || row.quietEnd() == null ? QuietHours.OFF
                    : new QuietHours(true, row.quietStart(), row.quietEnd());
            return new RecipientSettings(preferences.getOrDefault(user, Map.of()), quiet, row == null ? TenantTime.ZONE : safeZone(row.timezone()));
        }));
    }

    public RecipientSettings recipient(UUID userId) {
        return recipients(Set.of(userId)).get(userId);
    }

    private NotificationSettingsResponse view(UUID user) {
        RecipientSettings recipient = recipient(user);
        SettingsRow row = settings.find(user).orElseThrow();
        return new NotificationSettingsResponse(
                Arrays.stream(NotificationCategory.values()).map(category -> new NotificationSettingsResponse.CategoryPreference(category,
                        Arrays.stream(NotificationChannel.values()).collect(Collectors.toMap(Function.identity(),
                                channel -> recipient.channelsOf(category).contains(channel), (a, b) -> a, () -> new EnumMap<>(NotificationChannel.class))),
                        category.locked().stream().sorted().toList())).toList(),
                row.quietEnabled() || row.quietStart() != null
                        ? new NotificationSettingsResponse.QuietHoursView(row.quietEnabled(), hhmm(row.quietStart()), hhmm(row.quietEnd()))
                        : null,
                row.defaultReminderMinutes(), row.timezone(), row.version());
    }

    private static QuietHours quietHours(NotificationSettingsRequest.QuietHoursRequest request) {
        if (request == null || !request.enabled()) {
            return request == null || request.start() == null || request.end() == null ? QuietHours.OFF
                    : new QuietHours(false, LocalTime.parse(request.start()), LocalTime.parse(request.end()));
        }
        if (request.start() == null || request.end() == null) {
            throw ApiException.invalid("quietHours", "notification.quietHours.required");
        }
        return new QuietHours(true, LocalTime.parse(request.start()), LocalTime.parse(request.end()));
    }

    private static ZoneId zone(String timezone) {
        try {
            return ZoneId.of(timezone);
        } catch (DateTimeException e) {
            throw ApiException.invalid("timezone", "notification.timezone.invalid");
        }
    }

    private static ZoneId safeZone(String timezone) {
        try {
            return ZoneId.of(timezone);
        } catch (DateTimeException e) {
            return TenantTime.ZONE;
        }
    }

    private static String hhmm(LocalTime time) {
        return time == null ? null : "%02d:%02d".formatted(time.getHour(), time.getMinute());
    }
}
