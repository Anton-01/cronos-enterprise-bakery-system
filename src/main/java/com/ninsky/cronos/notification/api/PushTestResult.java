package com.ninsky.cronos.notification.api;

/** Outcome of a test push. */
public record PushTestResult(int delivered, int failed) {
}
