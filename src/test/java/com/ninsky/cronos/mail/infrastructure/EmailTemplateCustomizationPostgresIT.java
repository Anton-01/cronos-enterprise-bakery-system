package com.ninsky.cronos.mail.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ninsky.cronos.eventing.infrastructure.EventTypeRegistry;
import com.ninsky.cronos.eventing.infrastructure.OutboxCustomRepository;
import com.ninsky.cronos.eventing.infrastructure.OutboxDomainEventPublisher;
import com.ninsky.cronos.eventing.infrastructure.OutboxSignal;
import com.ninsky.cronos.eventing.infrastructure.SensitivePayloadCodec;
import com.ninsky.cronos.iam.audit.AuditRecorder;
import com.ninsky.cronos.iam.shared.Actor;
import com.ninsky.cronos.iam.shared.ActorProvider;
import com.ninsky.cronos.infrastructure.config.CacheConfig;
import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.infrastructure.observability.SqlObservabilityConfig;
import com.ninsky.cronos.infrastructure.observability.SqlStatementStats;
import com.ninsky.cronos.infrastructure.security.crypto.FieldEncryptionService;
import com.ninsky.cronos.mail.MailFixtures;
import com.ninsky.cronos.mail.api.EmailAssetResponse;
import com.ninsky.cronos.mail.api.EmailTemplateContent;
import com.ninsky.cronos.mail.application.CompiledTemplate;
import com.ninsky.cronos.mail.application.ContentValidator;
import com.ninsky.cronos.mail.application.EmailAssetService;
import com.ninsky.cronos.mail.application.EmailAssetStorage;
import com.ninsky.cronos.mail.application.EmailContentProvider;
import com.ninsky.cronos.mail.application.EmailSettingsService;
import com.ninsky.cronos.mail.application.EmailTemplateCommandService;
import com.ninsky.cronos.mail.application.EmailTemplateQueryService;
import com.ninsky.cronos.mail.application.MailProperties;
import com.ninsky.cronos.mail.domain.EmailBlock;
import com.ninsky.cronos.mail.domain.EmailLocale;
import com.ninsky.cronos.mail.domain.EmailSettings;
import com.ninsky.cronos.mail.domain.LocalizedEmail;
import com.ninsky.cronos.mail.domain.TemplateDefaults;
import com.ninsky.cronos.mail.domain.TemplateDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.ninsky.cronos.mail.MailFixtures.TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Email template customization against a real PostgreSQL running every migration (§10): overrides stored as
 * polymorphic JSONB, revision guards, the outbox event in the same transaction as the change, the render path served
 * from the caches without SQL, eviction only after commit, history, settings and deduplicated assets.
 */
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=true", "app.observability.sql.enabled=true",
        "app.frontend.url=https://app.cronos.mx"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({EmailOverrideCustomRepository.class, EmailSettingsCustomRepository.class, EmailAssetCustomRepository.class,
        EmailContentProvider.class, ContentValidator.class, EmailTemplateQueryService.class, EmailTemplateCommandService.class,
        EmailSettingsService.class, EmailAssetService.class, OutboxCustomRepository.class, OutboxDomainEventPublisher.class,
        SensitivePayloadCodec.class, OutboxSignal.class, SqlObservabilityConfig.class, EmailTemplateCustomizationPostgresIT.Beans.class})
class EmailTemplateCustomizationPostgresIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String KEY = "auth.password-reset";
    private static final Actor ADMIN = new Actor(UUID.fromString("0192f1c0-0000-7000-8000-000000000001"), "admin", Set.of(), false);

    @MockitoBean
    private ActorProvider actors;
    @MockitoBean
    private FieldEncryptionService encryption;
    @MockitoBean
    private AuditRecorder audit;

    @Autowired
    private EmailTemplateCommandService commands;
    @Autowired
    private EmailTemplateQueryService queries;
    @Autowired
    private EmailSettingsService settings;
    @Autowired
    private EmailAssetService assets;
    @Autowired
    private EmailContentProvider provider;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactions;

    @TestConfiguration
    static class Beans {
        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }

        @Bean
        ObjectMapper objectMapper() {
            return MailFixtures.JSON;
        }

        @Bean
        EventTypeRegistry eventTypeRegistry() {
            return new EventTypeRegistry();
        }

        @Bean
        MailProperties mailProperties() {
            return MailFixtures.properties(true);
        }

        @Bean
        TemplateDefaults templateDefaults() {
            return MailFixtures.DEFAULTS;
        }

        @Bean
        org.springframework.cache.CacheManager cacheManager() {
            return new CacheConfig(60, 500).cacheManager();
        }

        @Bean
        EmailAssetStorage emailAssetStorage() throws IOException {
            return new LocalEmailAssetStorage(Files.createTempDirectory("mail-assets"));
        }
    }

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM email_template_override");
        jdbc.update("DELETE FROM email_template_override_history");
        jdbc.update("DELETE FROM email_settings");
        jdbc.update("DELETE FROM email_asset");
        jdbc.update("DELETE FROM outbox_messages WHERE type LIKE 'mail.%'");
        provider.evict(com.ninsky.cronos.mail.application.EmailCacheEviction.settings(TENANT));
        provider.evict(com.ninsky.cronos.mail.application.EmailCacheEviction.content(TENANT, KEY, EmailLocale.ES_MX));
    }

    @Test
    void anOverrideRoundTripsAsPolymorphicJsonbAndServesTheNewContentAfterCommit() {
        TemplateDefinition template = provider.requireDefinition(KEY);
        assertThat(provider.effective(TENANT, template, EmailLocale.ES_MX).customized()).isFalse();

        var saved = commands.save(KEY, EmailLocale.ES_MX, draft(null, "Recupera tu acceso"), ADMIN);

        assertThat(saved.customizedLocales()).containsExactly(EmailLocale.ES_MX);
        assertThat(saved.content().get(EmailLocale.ES_MX).revision()).isEqualTo(1);
        assertThat(saved.content().get(EmailLocale.EN).revision()).isNull();
        assertThat(jdbc.queryForObject("SELECT blocks->1->>'type' FROM email_template_override", String.class)).isEqualTo("image");
        assertThat(jdbc.queryForObject("SELECT jsonb_typeof(blocks->1->'href') IS NULL FROM email_template_override", Boolean.class)).isTrue();
        // Evicted after commit: the render path sees the new content.
        assertThat(provider.effective(TENANT, template, EmailLocale.ES_MX).content().subject()).isEqualTo("Recupera tu acceso");
        assertThat(provider.effective(TENANT, template, EmailLocale.ES_MX).content().blocks().get(1))
                .isEqualTo(new EmailBlock.Image("https://assets.cronos.mx/mail/" + "a".repeat(64) + ".png", "Pastel", 240, null));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_messages WHERE type = 'mail.template-customized'", Integer.class))
                .isEqualTo(1);
        assertThat(queries.history(KEY, EmailLocale.ES_MX, 0, 10).content()).singleElement()
                .satisfies(entry -> {
                    assertThat(entry.action()).isEqualTo("SAVED");
                    assertThat(entry.subject()).isEqualTo("Recupera tu acceso");
                });
    }

    @Test
    void revisionsAreGuardedInTheDatabase() {
        commands.save(KEY, EmailLocale.ES_MX, draft(null, "Uno"), ADMIN);
        assertThat(catchThrowableOfType(ApiException.class, () -> commands.save(KEY, EmailLocale.ES_MX, draft(null, "Dos"), ADMIN))
                .primaryCode()).isEqualTo(ApiErrorCode.STALE_REVISION);
        commands.save(KEY, EmailLocale.ES_MX, draft(1L, "Dos"), ADMIN);
        assertThat(catchThrowableOfType(ApiException.class, () -> commands.save(KEY, EmailLocale.ES_MX, draft(1L, "Tres"), ADMIN))
                .primaryCode()).isEqualTo(ApiErrorCode.STALE_REVISION);
        assertThat(jdbc.queryForObject("SELECT revision FROM email_template_override", Long.class)).isEqualTo(2);
    }

    @Test
    void theRenderPathRunsNoSqlOnceCachedAndASecondRenderUsesTheCache() {
        TemplateDefinition template = provider.requireDefinition(KEY);
        provider.compiled(TENANT, template, EmailLocale.ES_MX);

        SqlStatementStats stats = SqlStatementStats.open();
        try {
            CompiledTemplate compiled = provider.compiled(TENANT, template, EmailLocale.ES_MX);
            assertThat(compiled.settings().sender().fromAddress()).isEqualTo("no-reply@cronos.mx");
            assertThat(stats.statements()).isZero();
        } finally {
            SqlStatementStats.close();
        }
    }

    @Test
    void aRolledBackSaveWritesNothingAndKeepsTheCachedContent() {
        TemplateDefinition template = provider.requireDefinition(KEY);
        String before = provider.effective(TENANT, template, EmailLocale.ES_MX).content().subject();

        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            commands.save(KEY, EmailLocale.ES_MX, draft(null, "Nunca"), ADMIN);
            throw new IllegalStateException("rollback");
        })).hasMessage("rollback");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM email_template_override", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_messages WHERE type LIKE 'mail.%'", Integer.class)).isZero();
        assertThat(provider.effective(TENANT, template, EmailLocale.ES_MX).content().subject()).isEqualTo(before);
    }

    @Test
    void resetIsIdempotentAndServesTheDefaultAgain() {
        TemplateDefinition template = provider.requireDefinition(KEY);
        commands.save(KEY, EmailLocale.ES_MX, draft(null, "Personalizado"), ADMIN);

        assertThat(commands.reset(KEY, EmailLocale.ES_MX, ADMIN).customizedLocales()).isEmpty();
        assertThat(commands.reset(KEY, EmailLocale.ES_MX, ADMIN).customizedLocales()).isEmpty();

        assertThat(provider.effective(TENANT, template, EmailLocale.ES_MX).content()).isEqualTo(template.content(EmailLocale.ES_MX));
        assertThat(queries.history(KEY, EmailLocale.ES_MX, 0, 10).content()).extracting(e -> e.action()).containsExactly("RESET", "SAVED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_messages WHERE type = 'mail.template-reset'", Integer.class)).isEqualTo(1);
    }

    @Test
    void savingTheDefaultDeletesTheOverride() {
        TemplateDefinition template = provider.requireDefinition(KEY);
        commands.save(KEY, EmailLocale.ES_MX, draft(null, "Personalizado"), ADMIN);

        commands.save(KEY, EmailLocale.ES_MX, EmailTemplateContent.of(1L, template.content(EmailLocale.ES_MX)), ADMIN);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM email_template_override", Integer.class)).isZero();
    }

    @Test
    void theCatalogEtagChangesAfterContentAndSettingsSaves() {
        String initial = queries.catalog("es-MX").etag();
        assertThat(queries.catalog("es-MX").etag()).isEqualTo(initial);

        commands.save(KEY, EmailLocale.ES_MX, draft(null, "Uno"), ADMIN);
        String afterContent = queries.catalog("es-MX").etag();
        assertThat(afterContent).isNotEqualTo(initial);
        assertThat(queries.catalog("es-MX").catalog().templates()).filteredOn(t -> t.key().equals(KEY)).singleElement()
                .satisfies(t -> assertThat(t.customizedLocales()).containsExactly(EmailLocale.ES_MX));

        EmailSettings current = settings.current();
        EmailSettings saved = settings.save(new EmailSettings(new com.ninsky.cronos.mail.domain.EmailBrand("Dulce Cronos",
                current.brand().primaryColor(), current.brand().accentColor(), null, null, current.brand().supportEmail(),
                current.brand().address(), current.brand().preferencesUrl()), current.sender(), null), ADMIN);
        assertThat(saved.revision()).isEqualTo(1);
        assertThat(queries.catalog("es-MX").etag()).isNotEqualTo(afterContent);
        assertThat(queries.catalog("es-MX").catalog().brand().name()).isEqualTo("Dulce Cronos");
        assertThat(provider.settings(TENANT).revision()).isEqualTo(1);
    }

    @Test
    void uploadingTheSameImageTwiceReturnsTheSameAssetAndSavesMarkItReferenced() throws IOException {
        BufferedImage image = new BufferedImage(30, 20, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(image, "png", png);

        EmailAssetResponse first = assets.upload(TENANT, png.toByteArray(), ADMIN);
        EmailAssetResponse second = assets.upload(TENANT, png.toByteArray(), ADMIN);

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(first.url()).matches("https://assets\\.cronos\\.mx/mail/[0-9a-f]{64}\\.png");
        assertThat(first.width()).isEqualTo(30);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_messages WHERE type = 'mail.asset-uploaded'", Integer.class)).isEqualTo(1);

        LocalizedEmail defaults = provider.requireDefinition(KEY).content(EmailLocale.ES_MX);
        List<EmailBlock> blocks = new java.util.ArrayList<>(defaults.blocks());
        blocks.add(new EmailBlock.Image(first.url(), "Logo", null, null));
        commands.save(KEY, EmailLocale.ES_MX, new EmailTemplateContent(null, defaults.subject(), defaults.preheader(), blocks), ADMIN);
        assertThat(jdbc.queryForObject("SELECT referenced FROM email_asset WHERE id = ?", Boolean.class, first.id())).isTrue();
    }

    private static EmailTemplateContent draft(Long revision, String subject) {
        return new EmailTemplateContent(revision, subject, "", List.of(new EmailBlock.Heading("Hola {{ displayName }}"),
                new EmailBlock.Image("https://assets.cronos.mx/mail/" + "a".repeat(64) + ".png", "Pastel", 240, null),
                new EmailBlock.Button("Elegir", "{{ resetUrl }}")));
    }
}
