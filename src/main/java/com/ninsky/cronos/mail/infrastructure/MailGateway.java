package com.ninsky.cronos.mail.infrastructure;

/**
 * Hands a rendered email to a provider (SMTP today; SES/SendGrid adapters fit behind the same port). Only the outbox
 * dispatcher calls it: no request thread ever talks to the mail server.
 */
public interface MailGateway {

    /**
     * Sends {@code mail}.
     *
     * @return the provider's id for the message
     * @throws RuntimeException when the provider refused or could not be reached (the outbox retries)
     */
    String send(OutgoingMail mail);
}
