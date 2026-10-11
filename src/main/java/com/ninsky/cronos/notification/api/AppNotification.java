package com.ninsky.cronos.notification.api;

import com.ninsky.cronos.notification.domain.NotificationCategory;
import com.ninsky.cronos.notification.domain.NotificationSeverity;
import com.ninsky.cronos.notification.domain.NotificationType;

import java.time.Instant;
import java.util.UUID;

/** {@code AppNotification} of the contract (§6.1); title and body were rendered in the recipient's language. */
public record AppNotification(UUID id, NotificationType type, NotificationCategory category, NotificationSeverity severity, String title,
                              String body, String link, String subjectType, UUID subjectId, String actorName, Instant createdAt,
                              Instant readAt) {
}
