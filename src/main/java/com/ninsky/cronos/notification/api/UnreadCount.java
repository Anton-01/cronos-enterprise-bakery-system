package com.ninsky.cronos.notification.api;

import com.ninsky.cronos.notification.domain.NotificationCategory;

import java.util.Map;

/** Unread notifications, in total and per category (categories without any are absent). */
public record UnreadCount(long total, Map<NotificationCategory, Long> byCategory) {

    public UnreadCount {
        byCategory = Map.copyOf(byCategory);
    }
}
