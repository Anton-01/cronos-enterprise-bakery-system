package com.ninsky.cronos.notification.domain;

import com.ninsky.cronos.eventing.domain.DomainEvent;
import com.ninsky.cronos.eventing.domain.EventType;
import com.ninsky.cronos.eventing.domain.RetrySchedule;
import com.ninsky.cronos.eventing.domain.Sensitive;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Events of the notification pipeline (§6.6): each channel is its own consumer of {@code notification.created}, so a
 * failing push never holds back the live frame. Rendered texts are encrypted in the outbox (they may name a client).
 */
public final class NotificationEvents {

    private NotificationEvents() {
    }

    /**
     * A notification was rendered for a user.
     *
     * @param stored true when it is in the inbox (IN_APP), so it is streamed live
     * @param push   true when it should also be pushed to the user's devices
     */
    @EventType(value = "notification.created", retry = RetrySchedule.PUSH)
    public record NotificationCreated(UUID notificationId, UUID userId, NotificationType type, NotificationSeverity severity,
                                      @Sensitive String title, @Sensitive String body, String link, String subjectType, UUID subjectId,
                                      Instant createdAt, boolean stored, boolean push) implements DomainEvent {
        @Override public String aggregateType() { return "NOTIFICATION"; }
        @Override public UUID aggregateId() { return notificationId; }
    }

    /** Notifications were read or removed; {@code ids} is empty when {@code all}. */
    @EventType("notification.read")
    public record NotificationsRead(UUID userId, List<UUID> ids, boolean all, long unread) implements DomainEvent {
        public NotificationsRead {
            ids = ids == null ? List.of() : List.copyOf(ids);
        }

        @Override public String aggregateType() { return "INBOX"; }
        @Override public UUID aggregateId() { return userId; }
    }

    /** A push could not be delivered for good (gone, too large, or the device failed too often). */
    @EventType("push.delivery-failed")
    public record PushDeliveryFailed(UUID subscriptionId, UUID userId, UUID notificationId, Integer httpStatus, boolean revoked)
            implements DomainEvent {
        @Override public String aggregateType() { return "PUSH_SUBSCRIPTION"; }
        @Override public UUID aggregateId() { return subscriptionId; }
    }
}
