package com.ninsky.cronos.identity.infrastructure;

import com.ninsky.cronos.domain.model.auth.SecurityNotification;
import com.ninsky.cronos.domain.port.auth.SecurityNotificationRepositoryPort;
import com.ninsky.cronos.eventing.application.OutboxHandler;
import com.ninsky.cronos.eventing.application.OutboxHandlers;
import com.ninsky.cronos.iam.shared.TenantTime;
import com.ninsky.cronos.iam.user.UserReadCustomRepository;
import com.ninsky.cronos.iam.user.UserRow;
import com.ninsky.cronos.identity.domain.IdentityEvents;
import com.ninsky.cronos.mail.application.MailRequest;
import com.ninsky.cronos.mail.application.Mailer;
import com.ninsky.cronos.mail.domain.MailLocales;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.HashMap;
import java.util.Map;

/**
 * Emails (and the legacy security-notification row) produced by identity events. Each handler is its own outbox
 * consumer, so a failing email never blocks the in-app notification of the same event, and vice versa.
 */
@Configuration
public class IdentityMailHandlers {

    private final UserReadCustomRepository users;
    private final Mailer mailer;
    private final String appUrl;

    public IdentityMailHandlers(UserReadCustomRepository users, Mailer mailer, @Value("${app.frontend.url}") String appUrl) {
        this.users = users;
        this.mailer = mailer;
        this.appUrl = appUrl;
    }

    /** {@code auth.new-sign-in} plus the security notification shown in the account's security panel. */
    @Bean
    OutboxHandler<IdentityEvents.NewDeviceSignedIn> newDeviceMail(SecurityNotificationRepositoryPort notifications, Clock clock) {
        return OutboxHandlers.of(IdentityEvents.NewDeviceSignedIn.class, "NewDeviceMail", (event, envelope) ->
                users.find(event.userId()).ifPresent(user -> {
                    String device = event.operatingSystem() + " " + event.browser();
                    notifications.save(SecurityNotification.builder()
                            .userId(user.id()).type("NEW_DEVICE_LOGIN").title("New login detected")
                            .message("We've detected a login from a new device: " + device)
                            .severity("WARNING").deviceName(device).ipAddress(event.ipAddress()).location(event.location())
                            .browser(event.browser()).operatingSystem(event.operatingSystem())
                            .read(false).emailSent(true).createdAt(TenantTime.nowLocal(clock)).emailSentAt(TenantTime.nowLocal(clock))
                            .build());
                    send(user, "auth.new-sign-in", Map.of(
                            "device", device,
                            "location", event.location() == null ? "" : event.location(),
                            "ipAddress", event.ipAddress() == null ? "" : event.ipAddress(),
                            "signedInAt", format(event.signedInAt(), user.locale()),
                            "securityUrl", appUrl + "/cronos/cuenta/seguridad"));
                }));
    }

    /** {@code auth.account-locked}: the lock end and the way out (a reset also unlocks). */
    @Bean
    OutboxHandler<IdentityEvents.AccountLocked> accountLockedMail() {
        return OutboxHandlers.of(IdentityEvents.AccountLocked.class, "AccountLockedMail", (event, envelope) ->
                users.find(event.userId()).ifPresent(user -> send(user, "auth.account-locked", Map.of(
                        "lockedUntil", format(event.lockedUntil(), user.locale()),
                        "resetUrl", appUrl + "/auth/forgot-password"))));
    }

    /** {@code auth.password-changed} after any change, reset or activation. */
    @Bean
    OutboxHandler<IdentityEvents.PasswordChanged> passwordChangedMail() {
        return OutboxHandlers.of(IdentityEvents.PasswordChanged.class, "PasswordChangedMail", (event, envelope) ->
                users.find(event.userId()).ifPresent(user -> send(user, "auth.password-changed", Map.of(
                        "changedAt", format(event.changedAt(), user.locale())))));
    }

    /** {@code auth.registration-attempt}: the owner learns someone used their address; the caller learns nothing. */
    @Bean
    OutboxHandler<IdentityEvents.RegistrationAttemptOnExistingEmail> registrationAttemptMail() {
        return OutboxHandlers.of(IdentityEvents.RegistrationAttemptOnExistingEmail.class, "RegistrationAttemptMail", (event, envelope) ->
                users.find(event.userId()).ifPresent(user -> send(user, "auth.registration-attempt", Map.of(
                        "signInUrl", appUrl + "/auth/login",
                        "resetUrl", appUrl + "/auth/forgot-password"))));
    }

    private void send(UserRow user, String template, Map<String, String> variables) {
        Map<String, String> all = new HashMap<>(variables);
        all.put("displayName", user.displayName());
        mailer.send(MailRequest.of(template, user.locale(), user.email(), all).forUser(user.id()));
    }

    static String format(Instant instant, String locale) {
        if (instant == null) {
            return "";
        }
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG, FormatStyle.SHORT)
                .withLocale(MailLocales.toLocale(locale)).withZone(TenantTime.ZONE).format(instant);
    }
}
