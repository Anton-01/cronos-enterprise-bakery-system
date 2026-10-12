package com.ninsky.cronos.identity.api;

/**
 * Outcome of an email verification.
 *
 * @param outcome         {@code VERIFIED} or {@code ALREADY_VERIFIED}
 * @param pendingApproval true while an administrator still has to approve the account
 */
public record VerifyEmailResult(String outcome, boolean pendingApproval) {
}
