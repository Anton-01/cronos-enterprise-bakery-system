package com.ninsky.cronos.mail.infrastructure;

import java.util.Map;

/**
 * A fully rendered email handed to a {@link MailGateway}.
 *
 * @param messageId value of the {@code Message-ID} header, including the angle brackets
 * @param replyTo   {@code Reply-To}, null for none
 * @param headers   extra headers ({@code List-Unsubscribe}, {@code X-Cronos-Template}, {@code X-Cronos-Trace}…)
 */
public record OutgoingMail(String messageId, String fromAddress, String fromName, String replyTo, String to, String subject,
                           String html, String text, Map<String, String> headers) {

    public OutgoingMail {
        headers = Map.copyOf(headers);
    }

    @Override
    public String toString() {
        return "OutgoingMail[messageId=" + messageId + "]";
    }
}
