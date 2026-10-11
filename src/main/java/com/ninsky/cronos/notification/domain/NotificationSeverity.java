package com.ninsky.cronos.notification.domain;

/** How a notification is presented; CRITICAL also breaks through quiet hours. */
public enum NotificationSeverity {
    INFO, SUCCESS, WARNING, CRITICAL
}
