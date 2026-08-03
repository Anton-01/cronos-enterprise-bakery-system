package com.ninsky.cronos.application.service.mail;

import com.ninsky.cronos.application.request.core.mail.EmailRequest;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import java.nio.charset.StandardCharsets;

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
            Context context = new Context();
            if (request.variables() != null) {
                context.setVariables(request.variables());
            }

            String htmlContent = templateEngine.process("emails/" + request.templateName(), context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());

            helper.setFrom(fromEmail, "Cronos Bakery System");
            helper.setTo(request.to());
            helper.setSubject(request.subject());
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Email sent successfully to: {}", request.to());

        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            log.error(":: CRONOS :: Critical error sending email to {}: {}", request.to(), e.getMessage(), e);
        }
    }
}