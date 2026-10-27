package com.ninsky.cronos.mail.infrastructure;

import com.ninsky.cronos.eventing.application.EventEnvelope;
import com.ninsky.cronos.infrastructure.config.CacheConfig;
import com.ninsky.cronos.mail.MailFixtures;
import com.ninsky.cronos.mail.application.EmailCacheEviction;
import com.ninsky.cronos.mail.application.EmailContentProvider;
import com.ninsky.cronos.mail.domain.EmailBlock;
import com.ninsky.cronos.mail.domain.EmailLocale;
import com.ninsky.cronos.mail.domain.LocalizedEmail;
import com.ninsky.cronos.mail.domain.TemplateDefinition;
import com.ninsky.cronos.mail.infrastructure.EmailOverrideCustomRepository.OverrideRow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static com.ninsky.cronos.mail.MailFixtures.TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * §5.2 with two application instances (each its own caches and Redis connection) sharing one database: after a save
 * committed on instance A, the outbox consumer's broadcast makes the next render use the new content on both
 * instances; replaying the same event changes nothing.
 */
@Testcontainers(disabledWithoutDocker = true)
class EmailCacheFanOutRedisIT {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private static final String KEY = "auth.password-reset";
    private static final TemplateDefinition TEMPLATE = MailFixtures.DEFAULTS.find(KEY).orElseThrow();

    /** The shared database row. */
    private final AtomicReference<Optional<OverrideRow>> stored = new AtomicReference<>(Optional.empty());
    private Instance a;
    private Instance b;

    /** One application instance: caches, Redis connection and invalidator. */
    record Instance(RedissonClient redis, EmailContentProvider provider, EmailCacheInvalidator invalidator) {
    }

    @BeforeEach
    void start() {
        a = instance();
        b = instance();
    }

    @AfterEach
    void stop() {
        for (Instance instance : List.of(a, b)) {
            instance.invalidator().unsubscribe();
            instance.redis().shutdown();
        }
    }

    @Test
    void aCommittedSaveReachesEveryInstanceAndReplaysAreHarmless() {
        assertThat(subject(a)).isEqualTo(TEMPLATE.content(EmailLocale.ES_MX).subject());
        assertThat(subject(b)).isEqualTo(TEMPLATE.content(EmailLocale.ES_MX).subject());

        stored.set(Optional.of(new OverrideRow(UUID.randomUUID(), KEY, EmailLocale.ES_MX,
                new LocalizedEmail("Nuevo asunto", "", List.of(new EmailBlock.Paragraph("x"))), 1, Instant.now(), UUID.randomUUID())));
        EventEnvelope envelope = envelope();
        a.invalidator().broadcast(envelope, EmailCacheEviction.content(TENANT, KEY, EmailLocale.ES_MX));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(subject(a)).isEqualTo("Nuevo asunto");
            assertThat(subject(b)).isEqualTo("Nuevo asunto");
        });

        // A redelivered event (same eventId) does not evict again: the newer cached value stays.
        stored.set(Optional.empty());
        a.invalidator().broadcast(envelope, EmailCacheEviction.content(TENANT, KEY, EmailLocale.ES_MX));
        await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(subject(b)).isEqualTo("Nuevo asunto"));
    }

    private String subject(Instance instance) {
        return instance.provider().effective(TENANT, TEMPLATE, EmailLocale.ES_MX).content().subject();
    }

    private Instance instance() {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
        RedissonClient redis = Redisson.create(config);
        EmailOverrideCustomRepository overrides = mock(EmailOverrideCustomRepository.class);
        when(overrides.find(any(), any(), any())).thenAnswer(call -> stored.get());
        EmailContentProvider provider = new EmailContentProvider(MailFixtures.DEFAULTS, overrides, mock(EmailSettingsCustomRepository.class),
                MailFixtures.properties(), new CacheConfig(60, 500).cacheManager(), "https://app.cronos.mx");
        EmailCacheInvalidator invalidator = new EmailCacheInvalidator(redis, provider, MailFixtures.JSON, "cronos:mail-cache:it");
        invalidator.subscribe();
        return new Instance(redis, provider, invalidator);
    }

    private static EventEnvelope envelope() {
        return new EventEnvelope(UUID.randomUUID(), "mail.template-customized", "EMAIL_TEMPLATE", UUID.randomUUID(), UUID.randomUUID(),
                null, "trace", null, Instant.now(), 0);
    }
}
