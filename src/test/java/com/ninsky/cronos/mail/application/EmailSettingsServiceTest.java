package com.ninsky.cronos.mail.application;

import com.ninsky.cronos.eventing.application.DomainEventPublisher;
import com.ninsky.cronos.iam.audit.AuditRecorder;
import com.ninsky.cronos.iam.shared.Actor;
import com.ninsky.cronos.infrastructure.config.CacheConfig;
import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.mail.MailFixtures;
import com.ninsky.cronos.mail.domain.EmailBrand;
import com.ninsky.cronos.mail.domain.EmailSender;
import com.ninsky.cronos.mail.domain.EmailSettings;
import com.ninsky.cronos.mail.domain.EmailTemplateEvents.EmailSettingsChangedEvent;
import com.ninsky.cronos.mail.infrastructure.EmailOverrideCustomRepository;
import com.ninsky.cronos.mail.infrastructure.EmailSettingsCustomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.ninsky.cronos.mail.MailFixtures.TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** §4.3: field validation, verified sending domains, revision rules and the changed-field event. */
class EmailSettingsServiceTest {

    private static final Actor ADMIN = new Actor(UUID.randomUUID(), "admin", Set.of(), false);
    private final EmailSettingsCustomRepository repository = mock(EmailSettingsCustomRepository.class);
    private final DomainEventPublisher events = mock(DomainEventPublisher.class);
    private final ApplicationEventPublisher localEvents = mock(ApplicationEventPublisher.class);
    private EmailSettingsService service;

    @BeforeEach
    void setUp() {
        EmailContentProvider provider = new EmailContentProvider(MailFixtures.DEFAULTS, mock(EmailOverrideCustomRepository.class), repository,
                MailFixtures.properties(true), new CacheConfig(60, 500).cacheManager(), "https://app.cronos.mx");
        service = new EmailSettingsService(provider, repository, mock(EmailAssetService.class), MailFixtures.properties(true), events,
                localEvents, mock(AuditRecorder.class), Clock.fixed(Instant.parse("2026-10-24T19:30:00Z"), ZoneOffset.UTC));
        when(repository.find(TENANT)).thenReturn(Optional.empty());
    }

    @Test
    void anUnverifiedFromDomainIsRefused() {
        ApiException error = catchThrowableOfType(ApiException.class,
                () -> service.save(settings("#10b981", "ventas@otro-dominio.com", null), ADMIN));
        assertThat(error.violations()).extracting(ApiException.Violation::code, ApiException.Violation::field)
                .containsExactly(tuple(ApiErrorCode.SENDER_DOMAIN_NOT_VERIFIED, "sender.fromAddress"));
    }

    @Test
    void badColorsAddressesAndUrlsAreFieldErrors() {
        EmailSettings bad = new EmailSettings(new EmailBrand("", "10b981", "#zzzzzz", "http://assets.cronos.mx/mail/l.png",
                "https://evil.example/b.png", "not-an-email", "CDMX", "javascript:x"),
                new EmailSender("Cronos", "Cronos <no-reply@cronos.mx>", "a@@b"), null);
        ApiException error = catchThrowableOfType(ApiException.class, () -> service.save(bad, ADMIN));
        assertThat(error.violations()).extracting(ApiException.Violation::field).containsExactlyInAnyOrder("brand.name", "brand.primaryColor",
                "brand.accentColor", "brand.logoUrl", "brand.headerImageUrl", "brand.supportEmail", "brand.preferencesUrl",
                "sender.fromAddress", "sender.replyTo");
    }

    @Test
    void theFirstSaveInsertsAndReportsTheChangedFields() {
        when(repository.insert(eq(TENANT), any(), eq(ADMIN.id()), any())).thenReturn(true);

        EmailSettings saved = service.save(settings("#123ABC", "hola@mail.cronos.mx", "ayuda@cronos.mx"), ADMIN);

        assertThat(saved.revision()).isEqualTo(1);
        assertThat(saved.brand().primaryColor()).isEqualTo("#123abc");
        ArgumentCaptor<EmailSettingsChangedEvent> event = ArgumentCaptor.forClass(EmailSettingsChangedEvent.class);
        verify(events).publish(event.capture());
        assertThat(event.getValue().changedFields()).containsExactly("brand.primaryColor", "sender.fromAddress", "sender.replyTo");
        assertThat(event.getValue().senderIdentityChanged()).isTrue();
        verify(localEvents).publishEvent(EmailCacheEviction.settings(TENANT));
    }

    @Test
    void revisionsFollowTheSameRulesAsContent() {
        EmailSettings stored = settings("#10b981", "no-reply@cronos.mx", null).withRevision(7L);
        when(repository.find(TENANT)).thenReturn(Optional.of(stored));
        assertThat(catchThrowableOfType(ApiException.class, () -> service.save(stored.withRevision(null), ADMIN)).primaryCode())
                .isEqualTo(ApiErrorCode.STALE_REVISION);
        assertThat(catchThrowableOfType(ApiException.class, () -> service.save(stored.withRevision(6L), ADMIN)).primaryCode())
                .isEqualTo(ApiErrorCode.STALE_REVISION);
    }

    private static EmailSettings settings(String color, String from, String replyTo) {
        return new EmailSettings(new EmailBrand("Cronos", color, "#f9a94c", null, null, "soporte@cronos.mx", "Ciudad de México, México",
                "https://app.cronos.mx/cronos/notificaciones?tab=preferences"), new EmailSender("Cronos Bakery", from, replyTo), null);
    }
}
