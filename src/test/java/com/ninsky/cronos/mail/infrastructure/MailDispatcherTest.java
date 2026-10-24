package com.ninsky.cronos.mail.infrastructure;

import com.ninsky.cronos.eventing.application.EventEnvelope;
import com.ninsky.cronos.infrastructure.config.CacheConfig;
import com.ninsky.cronos.mail.MailFixtures;
import com.ninsky.cronos.mail.application.EmailContentProvider;
import com.ninsky.cronos.mail.application.MailRequested;
import com.ninsky.cronos.mail.domain.EmailBlock;
import com.ninsky.cronos.mail.domain.EmailBrand;
import com.ninsky.cronos.mail.domain.EmailSender;
import com.ninsky.cronos.mail.domain.EmailSettings;
import com.ninsky.cronos.mail.domain.LocalizedEmail;
import com.ninsky.cronos.mail.domain.TemplateDefinition;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.ninsky.cronos.mail.MailFixtures.TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** §8: effective content and settings drive subject, envelope (From, Reply-To) and List-Unsubscribe. */
class MailDispatcherTest {

    private final EmailOverrideCustomRepository overrides = mock(EmailOverrideCustomRepository.class);
    private final EmailSettingsCustomRepository settings = mock(EmailSettingsCustomRepository.class);
    private final MailGateway gateway = mock(MailGateway.class);
    private MailDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        EmailContentProvider provider = new EmailContentProvider(MailFixtures.DEFAULTS, overrides, settings, MailFixtures.properties(),
                new CacheConfig(60, 500).cacheManager(), "https://app.cronos.mx");
        dispatcher = new MailDispatcher(provider, new ThymeleafEmailRenderer(MailFixtures.engine()), gateway,
                mock(MailMessageCustomRepository.class), MailFixtures.properties(), mock(PlatformTransactionManager.class),
                new SimpleMeterRegistry(), Clock.systemUTC());
        when(overrides.find(any(), any(), any())).thenReturn(Optional.empty());
        when(settings.find(TENANT)).thenReturn(Optional.of(new EmailSettings(new EmailBrand("Dulce", "#123abc", "#abcdef", null, null,
                "ayuda@dulce.mx", "Puebla", "https://app.cronos.mx/prefs"), new EmailSender("Dulce Cronos", "hola@mail.cronos.mx",
                "ayuda@dulce.mx"), 4L)));
        when(gateway.send(any())).thenReturn("provider-id");
    }

    @Test
    void theSettingsDriveTheEnvelopeAndTheUnsubscribeHeader() {
        TemplateDefinition pending = MailFixtures.DEFAULTS.find("admin.registration-pending").orElseThrow();

        dispatcher.handle(new MailRequested(UUID.randomUUID(), pending.key(), "es-MX", "admin@cronos.mx", pending.samples(), null, false, null),
                envelope());

        OutgoingMail mail = sent();
        assertThat(mail.fromName()).isEqualTo("Dulce Cronos");
        assertThat(mail.fromAddress()).isEqualTo("hola@mail.cronos.mx");
        assertThat(mail.replyTo()).isEqualTo("ayuda@dulce.mx");
        assertThat(mail.headers().get("List-Unsubscribe")).isEqualTo("<mailto:ayuda@dulce.mx?subject=unsubscribe>, <https://app.cronos.mx/prefs>");
        assertThat(mail.html()).contains("Dulce").contains("#123abc");
        assertThat(mail.text()).endsWith("-- \nDulce\n");
    }

    @Test
    void aTestSendRendersItsDraftWithTheTestPrefix() {
        TemplateDefinition reset = MailFixtures.DEFAULTS.find("auth.password-reset").orElseThrow();
        LocalizedEmail draft = new LocalizedEmail("Borrador para {{ displayName }}", "", List.of(new EmailBlock.Paragraph("Hola")));

        dispatcher.handle(new MailRequested(UUID.randomUUID(), reset.key(), "es-MX", "admin@cronos.mx", reset.samples(), null, true, draft),
                envelope());

        OutgoingMail mail = sent();
        assertThat(mail.subject()).isEqualTo("[TEST] Borrador para Ana López");
        assertThat(mail.headers()).doesNotContainKey("List-Unsubscribe");
    }

    private OutgoingMail sent() {
        ArgumentCaptor<OutgoingMail> mail = ArgumentCaptor.forClass(OutgoingMail.class);
        verify(gateway).send(mail.capture());
        return mail.getValue();
    }

    private static EventEnvelope envelope() {
        return new EventEnvelope(UUID.randomUUID(), "mail.requested", "MAIL", UUID.randomUUID(), UUID.randomUUID(), null, "trace", null,
                Instant.now(), 0);
    }
}
