package com.ninsky.cronos.notification.api;

import java.util.UUID;

/** {@code POST /notifications/push/test}: one device, or every device when {@code deviceId} is null. */
public record PushTestRequest(UUID deviceId) {
}
