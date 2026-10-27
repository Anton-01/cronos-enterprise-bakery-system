package com.ninsky.cronos.mail.application;

import com.ninsky.cronos.eventing.application.DomainEventPublisher;
import com.ninsky.cronos.iam.audit.AuditEvent;
import com.ninsky.cronos.iam.audit.AuditRecorder;
import com.ninsky.cronos.iam.shared.Actor;
import com.ninsky.cronos.infrastructure.config.CacheConfig;
import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.mail.MailFixtures;
import com.ninsky.cronos.mail.api.EmailTemplateContent;
import com.ninsky.cronos.mail.domain.EmailBlock;
import com.ninsky.cronos.mail.domain.EmailLocale;
import com.ninsky.cronos.mail.domain.EmailTemplateEvents.EmailTemplateCustomizedEvent;
import com.ninsky.cronos.mail.domain.EmailTemplateEvents.EmailTemplateResetEvent;
import com.ninsky.cronos.mail.domain.LocalizedEmail;
import com.ninsky.cronos.mail.domain.TemplateDefinition;
import com.ninsky.cronos.mail.infrastructure.EmailOverrideCustomRepository;
import com.ninsky.cronos.mail.infrastructure.EmailOverrideCustomRepository.OverrideRow;
import com.ninsky.cronos.mail.infrastructure.EmailSettingsCustomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.ninsky.cronos.mail.MailFixtures.TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** §4.1 / §4.2: revision rules, default-equal content, history, outbox event, audit and after-commit eviction. */
class EmailTemplateCommandServiceTest {

    private static final String KEY = "auth.password-reset";
    private static final TemplateDefinition RESET = MailFixtures.DEFAULTS.find(KEY).orElseThrow();
    private static final Instant NOW = Instant.parse("2026-10-24T19:30:00Z");
    private static final Actor ADMIN = new Actor(UUID.randomUUID(), "admin", Set.of(), false);

    private final EmailOverrideCustomRepository overrides = mock(EmailOverrideCustomRepository.class);
    private final DomainEventPublisher events = mock(DomainEventPublisher.class);
    private final ApplicationEventPublisher localEvents = mock(ApplicationEventPublisher.class);
    private final AuditRecorder audit = mock(AuditRecorder.class);
    private final EmailAssetService assets = mock(EmailAssetService.class);
    private EmailTemplateCommandService service;

    @BeforeEach
    void setUp() {
        EmailContentProvider provider = new EmailContentProvider(MailFixtures.DEFAULTS, overrides, mock(EmailSettingsCustomRepository.class),
                MailFixtures.properties(), new CacheConfig(60, 500).cacheManager(), "https://app.cronos.mx");
        service = new EmailTemplateCommandService(provider, overrides, new ContentValidator(MailFixtures.properties()), assets,
                new EmailTemplateQueryService(provider, overrides), events, localEvents, audit, Clock.fixed(NOW, ZoneOffset.UTC));
        when(overrides.find(any(), any(), any())).thenReturn(Optional.empty());
        when(overrides.displayNames(any())).thenReturn(Map.of());
    }

    @Test
    void theFirstSaveInsertsRevisionOneWithHistoryEventAuditAndEviction() {
        when(overrides.insert(eq(TENANT), eq(KEY), eq(EmailLocale.ES_MX), any(), eq(ADMIN.id()), eq(NOW))).thenReturn(true);

        service.save(KEY, EmailLocale.ES_MX, edited(null), ADMIN);

        verify(overrides).history(eq(TENANT), eq(KEY), eq(EmailLocale.ES_MX), eq(1L), eq("SAVED"), eq(edited(null).toContent()), eq(ADMIN.id()),
                eq(NOW), any());
        ArgumentCaptor<EmailTemplateCustomizedEvent> event = ArgumentCaptor.forClass(EmailTemplateCustomizedEvent.class);
        verify(events).publish(event.capture());
        assertThat(event.getValue()).isEqualTo(new EmailTemplateCustomizedEvent(TENANT, KEY, EmailLocale.ES_MX, 1, List.of("subject")));
        ArgumentCaptor<AuditEvent> entry = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).record(entry.capture());
        assertThat(entry.getValue().changes()).containsOnlyKeys("subject");
        verify(localEvents).publishEvent(EmailCacheEviction.content(TENANT, KEY, EmailLocale.ES_MX));
    }

    @Test
    void aNullRevisionWhileAnOverrideExistsIsStale() {
        when(overrides.find(TENANT, KEY, EmailLocale.ES_MX)).thenReturn(Optional.of(row(4)));

        ApiException error = catchThrowableOfType(ApiException.class, () -> service.save(KEY, EmailLocale.ES_MX, edited(null), ADMIN));

        assertThat(error.primaryCode()).isEqualTo(ApiErrorCode.STALE_REVISION);
        assertThat(error.status().value()).isEqualTo(409);
        verify(overrides, never()).insert(any(), any(), any(), any(), any(), any());
    }

    @Test
    void aNumericRevisionThatMovedOnIsStale() {
        when(overrides.find(TENANT, KEY, EmailLocale.ES_MX)).thenReturn(Optional.of(row(4)));
        assertThat(catchThrowableOfType(ApiException.class, () -> service.save(KEY, EmailLocale.ES_MX, edited(3L), ADMIN)).primaryCode())
                .isEqualTo(ApiErrorCode.STALE_REVISION);

        // A concurrent writer between the read and the guarded UPDATE: 0 rows.
        when(overrides.update(eq(TENANT), eq(KEY), eq(EmailLocale.ES_MX), eq(4L), any(), any(), any())).thenReturn(false);
        assertThat(catchThrowableOfType(ApiException.class, () -> service.save(KEY, EmailLocale.ES_MX, edited(4L), ADMIN)).primaryCode())
                .isEqualTo(ApiErrorCode.STALE_REVISION);
        verifyNoInteractions(events);
    }

    @Test
    void aMatchingRevisionUpdatesToTheNextOne() {
        when(overrides.find(TENANT, KEY, EmailLocale.ES_MX)).thenReturn(Optional.of(row(4)));
        when(overrides.update(eq(TENANT), eq(KEY), eq(EmailLocale.ES_MX), eq(4L), any(), any(), any())).thenReturn(true);

        service.save(KEY, EmailLocale.ES_MX, new EmailTemplateContent(4L, "Otro asunto", "", RESET.content(EmailLocale.ES_MX).blocks()), ADMIN);

        verify(events).publish(new EmailTemplateCustomizedEvent(TENANT, KEY, EmailLocale.ES_MX, 5, List.of("subject", "preheader", "blocks")));
    }

    @Test
    void savingTheDefaultDeletesTheOverrideInsteadOfStoringACopy() {
        when(overrides.find(TENANT, KEY, EmailLocale.ES_MX)).thenReturn(Optional.of(row(2)));
        when(overrides.delete(TENANT, KEY, EmailLocale.ES_MX, 2L)).thenReturn(Optional.of(row(2)));

        service.save(KEY, EmailLocale.ES_MX, EmailTemplateContent.of(2L, RESET.content(EmailLocale.ES_MX)), ADMIN);

        verify(overrides, never()).update(any(), any(), any(), anyLong(), any(), any(), any());
        verify(overrides).history(eq(TENANT), eq(KEY), eq(EmailLocale.ES_MX), eq(2L), eq("RESET"), isNull(), eq(ADMIN.id()), eq(NOW), any());
        verify(events).publish(new EmailTemplateResetEvent(TENANT, KEY, EmailLocale.ES_MX, 2));
    }

    @Test
    void invalidContentIsRejectedBeforeAnyWrite() {
        EmailTemplateContent bad = new EmailTemplateContent(null, "Hola {{ nope }}", "", List.of(new EmailBlock.Button("x", "javascript:x")));
        ApiException error = catchThrowableOfType(ApiException.class, () -> service.save(KEY, EmailLocale.ES_MX, bad, ADMIN));
        assertThat(error.violations()).extracting(ApiException.Violation::field).containsExactlyInAnyOrder("subject", "blocks[0].url");
        verify(overrides, never()).insert(any(), any(), any(), any(), any(), any());
    }

    @Test
    void anUnknownTemplateIs404() {
        assertThat(catchThrowableOfType(ApiException.class, () -> service.reset("nope", EmailLocale.EN, ADMIN)).primaryCode())
                .isEqualTo(ApiErrorCode.EMAIL_TEMPLATE_NOT_FOUND);
    }

    @Test
    void resetIsIdempotent() {
        when(overrides.delete(TENANT, KEY, EmailLocale.EN)).thenReturn(Optional.empty());

        assertThat(service.reset(KEY, EmailLocale.EN, ADMIN).customizedLocales()).isEmpty();

        verifyNoInteractions(events, audit);
        verify(overrides, never()).history(any(), any(), any(), anyLong(), any(), any(), any(), any(), any());
    }

    private static EmailTemplateContent edited(Long revision) {
        LocalizedEmail defaults = RESET.content(EmailLocale.ES_MX);
        return new EmailTemplateContent(revision, "Recupera tu acceso, {{ displayName }}", defaults.preheader(), defaults.blocks());
    }

    private static OverrideRow row(long revision) {
        return new OverrideRow(UUID.randomUUID(), KEY, EmailLocale.ES_MX, new LocalizedEmail("Asunto viejo", "pre", List.of(new EmailBlock.Paragraph("x"))),
                revision, NOW, ADMIN.id());
    }
}
