package com.ninsky.cronos.mail.domain;

/**
 * Envelope identity of every email.
 *
 * @param fromAddress must belong to a verified sending domain (SPF/DKIM aligned)
 * @param replyTo     optional {@code Reply-To}
 */
public record EmailSender(String fromName, String fromAddress, String replyTo) {
}
