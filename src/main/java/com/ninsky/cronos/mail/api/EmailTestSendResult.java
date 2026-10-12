package com.ninsky.cronos.mail.api;

import java.util.UUID;

/** The queued test email; follow it in {@code /admin/events?aggregateId=messageId}. */
public record EmailTestSendResult(UUID messageId) {
}
