package com.ninsky.cronos.notification.infrastructure.stream;

import java.util.UUID;

/**
 * One message for the open streams of a user, as it travels between instances (§6.3). {@code CLOSE} frames end the
 * connections of {@code sessionId} (or every connection of the user when it is null).
 *
 * @param id   SSE id (notification frames only, so {@code Last-Event-ID} always names a notification)
 * @param data JSON body of the frame
 */
public record StreamFrame(Kind kind, UUID userId, UUID sessionId, String event, String id, String data) {

    /** What the receiving instance does with the frame. */
    public enum Kind { SEND, CLOSE }

    public static StreamFrame send(UUID userId, String event, String id, String data) {
        return new StreamFrame(Kind.SEND, userId, null, event, id, data);
    }

    public static StreamFrame close(UUID userId, UUID sessionId) {
        return new StreamFrame(Kind.CLOSE, userId, sessionId, null, null, null);
    }
}
