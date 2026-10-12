package com.ninsky.cronos.notification.domain;

/** Stable notification codes (§6.1); the category and severity are fixed here, never sent by clients. */
public enum NotificationType {
    QUOTE_VIEWED(NotificationCategory.QUOTES, NotificationSeverity.INFO),
    QUOTE_ACCEPTED(NotificationCategory.QUOTES, NotificationSeverity.SUCCESS),
    QUOTE_REJECTED(NotificationCategory.QUOTES, NotificationSeverity.WARNING),
    QUOTE_EXPIRING(NotificationCategory.QUOTES, NotificationSeverity.WARNING),
    ORDER_SCHEDULED(NotificationCategory.ORDERS, NotificationSeverity.SUCCESS),
    ORDER_RESCHEDULED(NotificationCategory.ORDERS, NotificationSeverity.INFO),
    ORDER_CANCELLED(NotificationCategory.ORDERS, NotificationSeverity.WARNING),
    EVENT_REMINDER(NotificationCategory.CALENDAR, NotificationSeverity.INFO),
    PRODUCTION_DUE(NotificationCategory.CALENDAR, NotificationSeverity.WARNING),
    REGISTRATION_SUBMITTED(NotificationCategory.ADMIN, NotificationSeverity.INFO),
    REGISTRATION_APPROVED(NotificationCategory.ACCOUNT, NotificationSeverity.SUCCESS),
    PASSWORD_CHANGED(NotificationCategory.ACCOUNT, NotificationSeverity.INFO),
    NEW_SIGN_IN(NotificationCategory.SECURITY, NotificationSeverity.WARNING),
    ACCOUNT_LOCKED(NotificationCategory.SECURITY, NotificationSeverity.CRITICAL),
    SYSTEM_ANNOUNCEMENT(NotificationCategory.SYSTEM, NotificationSeverity.INFO);

    private final NotificationCategory category;
    private final NotificationSeverity severity;

    NotificationType(NotificationCategory category, NotificationSeverity severity) {
        this.category = category;
        this.severity = severity;
    }

    public NotificationCategory category() {
        return category;
    }

    public NotificationSeverity severity() {
        return severity;
    }
}
