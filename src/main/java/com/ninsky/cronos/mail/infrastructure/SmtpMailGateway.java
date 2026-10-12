package com.ninsky.cronos.mail.infrastructure;

import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/**
 * {@link MailGateway} over {@link JavaMailSender}: {@code multipart/alternative} with the plain-text and HTML parts,
 * and a stable {@code Message-ID} (the outbox event id) so a redelivered message is recognisable upstream.
 */
@Component
public class SmtpMailGateway implements MailGateway {

    private final JavaMailSender sender;

    public SmtpMailGateway(JavaMailSender sender) {
        this.sender = sender;
    }

    @Override
    public String send(OutgoingMail mail) {
        Session session = sender.createMimeMessage().getSession();
        MimeMessage message = new FixedIdMimeMessage(session, mail.messageId());
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name());
            helper.setFrom(mail.fromAddress(), mail.fromName());
            helper.setTo(mail.to());
            helper.setSubject(mail.subject());
            helper.setText(mail.text(), mail.html());
            for (var header : mail.headers().entrySet()) {
                message.setHeader(header.getKey(), header.getValue());
            }
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new MailPreparationException("Could not build the email message", e);
        }
        sender.send(message);
        return mail.messageId();
    }

    /** Keeps the Message-ID we chose instead of the one JavaMail generates on save. */
    static final class FixedIdMimeMessage extends MimeMessage {

        private final String messageId;

        FixedIdMimeMessage(Session session, String messageId) {
            super(session);
            this.messageId = messageId;
        }

        @Override
        protected void updateMessageID() throws MessagingException {
            setHeader("Message-ID", messageId);
        }
    }
}
