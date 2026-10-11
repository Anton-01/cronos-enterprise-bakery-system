package com.ninsky.cronos.notification.api;

/** Whether Web Push works and the VAPID public key the browser subscribes with. */
public record PushConfig(boolean enabled, String vapidPublicKey) {
}
