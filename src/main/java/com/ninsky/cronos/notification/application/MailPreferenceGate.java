package com.ninsky.cronos.notification.application;

import com.ninsky.cronos.mail.application.MailPreferences;
import com.ninsky.cronos.notification.domain.NotificationCategory;
import com.ninsky.cronos.notification.domain.NotificationChannel;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/** The EMAIL switch of the notification preferences, applied to non-transactional templates by their category. */
@Component
public class MailPreferenceGate implements MailPreferences {

    /** Template key prefix → preference category; anything else is an ACCOUNT email. */
    static final Map<String, NotificationCategory> CATEGORY_BY_PREFIX = Map.of(
            "admin.", NotificationCategory.ADMIN,
            "quote.", NotificationCategory.QUOTES,
            "calendar.", NotificationCategory.CALENDAR,
            "auth.new-sign-in", NotificationCategory.SECURITY);

    private final NotificationSettingsService settings;

    public MailPreferenceGate(NotificationSettingsService settings) {
        this.settings = settings;
    }

    @Override
    public boolean allows(UUID userId, String templateKey) {
        return settings.recipient(userId).channelsOf(categoryOf(templateKey)).contains(NotificationChannel.EMAIL);
    }

    static NotificationCategory categoryOf(String templateKey) {
        return CATEGORY_BY_PREFIX.entrySet().stream().filter(entry -> templateKey.startsWith(entry.getKey())).map(Map.Entry::getValue)
                .findFirst().orElse(NotificationCategory.ACCOUNT);
    }
}
