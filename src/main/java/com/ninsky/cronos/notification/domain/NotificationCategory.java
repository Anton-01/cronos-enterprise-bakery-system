package com.ninsky.cronos.notification.domain;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Preference group of notification types (§6.5) with its default channels and the channels a user cannot turn off.
 */
public enum NotificationCategory {
    ORDERS(EnumSet.allOf(NotificationChannel.class), EnumSet.noneOf(NotificationChannel.class)),
    QUOTES(EnumSet.allOf(NotificationChannel.class), EnumSet.noneOf(NotificationChannel.class)),
    CALENDAR(EnumSet.allOf(NotificationChannel.class), EnumSet.noneOf(NotificationChannel.class)),
    ACCOUNT(EnumSet.allOf(NotificationChannel.class), EnumSet.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL)),
    SECURITY(EnumSet.allOf(NotificationChannel.class), EnumSet.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL)),
    ADMIN(EnumSet.of(NotificationChannel.IN_APP, NotificationChannel.PUSH), EnumSet.noneOf(NotificationChannel.class)),
    SYSTEM(EnumSet.of(NotificationChannel.IN_APP), EnumSet.of(NotificationChannel.IN_APP));

    private final Set<NotificationChannel> defaults;
    private final Set<NotificationChannel> locked;

    NotificationCategory(EnumSet<NotificationChannel> defaults, EnumSet<NotificationChannel> locked) {
        this.defaults = Collections.unmodifiableSet(defaults);
        this.locked = Collections.unmodifiableSet(locked);
    }

    /** Channels of a user who never changed the category. */
    public Set<NotificationChannel> defaults() {
        return defaults;
    }

    /** Channels that stay on whatever the user writes. */
    public Set<NotificationChannel> locked() {
        return locked;
    }

    /** {@code requested} plus the locked channels. */
    public EnumSet<NotificationChannel> effective(Set<NotificationChannel> requested) {
        EnumSet<NotificationChannel> channels = EnumSet.noneOf(NotificationChannel.class);
        channels.addAll(requested);
        channels.addAll(locked);
        return channels;
    }
}
