package com.ninsky.cronos.application.listener;

import com.ninsky.cronos.application.event.NewDeviceLoginEvent;
import com.ninsky.cronos.application.request.core.mail.EmailRequest;
import com.ninsky.cronos.application.service.mail.MailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityEventListener {

    private final MailService mailService;

    @EventListener
    public void handleNewDeviceLoginEvent(NewDeviceLoginEvent event) {
        log.info("New device event detected. Preparing email to: {}", event.email());
        String formattedTime = event.time().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));

        EmailRequest request = EmailRequest.builder().to(event.email()).subject("Security Alert: New login detected")
                .templateName("auth/new-device").variables(Map.of("username", event.username(),
                        "deviceName", event.deviceName(), "location", event.location(),
                        "ipAddress", event.ipAddress(), "time", formattedTime)).build();

        mailService.sendHtmlEmail(request);
    }
}
