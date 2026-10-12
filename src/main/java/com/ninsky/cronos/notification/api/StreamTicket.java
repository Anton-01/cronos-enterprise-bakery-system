package com.ninsky.cronos.notification.api;

import java.time.Instant;

/** Single-use ticket that authenticates {@code GET /notifications/stream} for 60 seconds. */
public record StreamTicket(String ticket, Instant expiresAt) {

    @Override
    public String toString() {
        return "StreamTicket[expiresAt=" + expiresAt + "]";
    }
}
