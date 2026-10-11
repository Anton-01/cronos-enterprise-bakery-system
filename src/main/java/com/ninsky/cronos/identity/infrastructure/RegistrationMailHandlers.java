package com.ninsky.cronos.identity.infrastructure;

import com.ninsky.cronos.eventing.application.OutboxHandler;
import com.ninsky.cronos.eventing.application.OutboxHandlers;
import com.ninsky.cronos.iam.access.PermissionHolderCustomRepository;
import com.ninsky.cronos.iam.permission.Permissions;
import com.ninsky.cronos.iam.shared.Masking;
import com.ninsky.cronos.iam.user.UserReadCustomRepository;
import com.ninsky.cronos.iam.user.UserRow;
import com.ninsky.cronos.identity.domain.RegistrationEvents;
import com.ninsky.cronos.mail.application.MailRequest;
import com.ninsky.cronos.mail.application.Mailer;
import com.ninsky.cronos.mail.domain.MailLocales;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Map;

/**
 * Emails of the registration lifecycle: receipt to the applicant, review alert to every reviewer (coalesced per
 * reviewer in 10-minute buckets so a burst of sign-ups is one email), approval and — when asked — rejection.
 */
@Configuration
public class RegistrationMailHandlers {

    static final Duration DIGEST_BUCKET = Duration.ofMinutes(10);

    private final UserReadCustomRepository users;
    private final Mailer mailer;
    private final String appUrl;

    public RegistrationMailHandlers(UserReadCustomRepository users, Mailer mailer, @Value("${app.frontend.url}") String appUrl) {
        this.users = users;
        this.mailer = mailer;
        this.appUrl = appUrl;
    }

    @Bean
    OutboxHandler<RegistrationEvents.RegistrationSubmitted> registrationReceivedMail() {
        return OutboxHandlers.of(RegistrationEvents.RegistrationSubmitted.class, "RegistrationReceivedMail", (event, envelope) ->
                users.find(event.userId()).ifPresent(applicant -> mailer.send(MailRequest.of("auth.registration-received",
                        applicant.locale(), applicant.email(),
                        Map.of("displayName", applicant.displayName(), "reference", event.reference())).forUser(applicant.id()))));
    }

    @Bean
    OutboxHandler<RegistrationEvents.RegistrationSubmitted> registrationReviewMail(PermissionHolderCustomRepository holders) {
        return OutboxHandlers.of(RegistrationEvents.RegistrationSubmitted.class, "RegistrationReviewMail", (event, envelope) ->
                users.find(event.userId()).ifPresent(applicant -> {
                    long bucket = envelope.occurredAt().getEpochSecond() / DIGEST_BUCKET.toSeconds();
                    for (UserRow reviewer : users.findAll(holders.holders(Permissions.IAM_REGISTRATION_DECIDE))) {
                        mailer.send(MailRequest.of("admin.registration-pending", reviewer.locale(), reviewer.email(), Map.of(
                                        "applicantName", applicant.displayName(),
                                        "applicantEmail", Masking.email(applicant.email()),
                                        "reference", event.reference(),
                                        "reviewUrl", appUrl + "/cronos/admin/solicitudes-registro"))
                                .forUser(reviewer.id())
                                .dedupedBy("registration-pending:" + reviewer.id() + ":" + bucket));
                    }
                }));
    }

    @Bean
    OutboxHandler<RegistrationEvents.RegistrationApproved> accountApprovedMail() {
        return OutboxHandlers.of(RegistrationEvents.RegistrationApproved.class, "AccountApprovedMail", (event, envelope) ->
                users.find(event.userId()).ifPresent(user -> mailer.send(MailRequest.of("auth.account-approved", user.locale(),
                        user.email(), Map.of("displayName", user.displayName(), "signInUrl", appUrl + "/auth/login")).forUser(user.id()))));
    }

    @Bean
    OutboxHandler<RegistrationEvents.RegistrationRejected> accountRejectedMail(MessageSource messages) {
        return OutboxHandlers.of(RegistrationEvents.RegistrationRejected.class, "AccountRejectedMail", (event, envelope) -> {
            if (!event.notifyApplicant()) {
                return;
            }
            users.find(event.userId()).ifPresent(user -> mailer.send(MailRequest.of("auth.account-rejected", user.locale(), user.email(),
                    Map.of("displayName", user.displayName(),
                            "reason", messages.getMessage("registration.rejectReason." + event.reason().name(), null,
                                    event.reason().name(), MailLocales.toLocale(user.locale())),
                            "comment", event.comment() == null ? "" : event.comment())).forUser(user.id())));
        });
    }
}
