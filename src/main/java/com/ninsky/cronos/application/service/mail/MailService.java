package com.ninsky.cronos.application.service.mail;

import com.ninsky.cronos.application.request.core.mail.EmailRequest;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;

    @Value("${app.mail.sender-address}")
    private String fromEmail;

    /**
     * @Async indicates that this method will run in a separate thread (mailTaskExecutor).
     */
    @Async("mailTaskExecutor")
    public void sendHtmlEmail(EmailRequest request) {
        log.info("Starting asynchronous email delivery to: {} | Asunto: {}", request.to(), request.subject());
        try {
            sendNow(request, Locale.getDefault());
            log.info("Email sent successfully to: {}", request.to());
        } catch (MailException e) {
            log.error(":: CRONOS :: Critical error sending email to {}: {}", request.to(), e.getMessage(), e);
        }
    }

    /** Synchronous send in the caller's thread; failures propagate so the caller can retry or record them. */
    public void sendNow(EmailRequest request, Locale locale) {
        Context context = new Context(locale);
        if (request.variables() != null) {
            context.setVariables(request.variables());
        }
        String htmlContent = templateEngine.process("emails/" + request.templateName(), context);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());
            helper.setFrom(fromEmail, "Cronos Bakery System");
            helper.setTo(request.to());
            helper.setSubject(request.subject());
            helper.setText(htmlContent, true);
            mailSender.send(message);
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new MailPreparationException("Could not build the email message", e);
        }
    }
}
