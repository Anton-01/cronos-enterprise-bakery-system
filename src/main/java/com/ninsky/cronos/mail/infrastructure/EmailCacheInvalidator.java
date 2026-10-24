package com.ninsky.cronos.mail.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ninsky.cronos.eventing.application.EventEnvelope;
import com.ninsky.cronos.eventing.application.OutboxHandler;
import com.ninsky.cronos.eventing.application.OutboxHandlers;
import com.ninsky.cronos.mail.application.EmailCacheEviction;
import com.ninsky.cronos.mail.application.EmailContentProvider;
import com.ninsky.cronos.mail.domain.EmailTemplateEvents.EmailSettingsChangedEvent;
import com.ninsky.cronos.mail.domain.EmailTemplateEvents.EmailTemplateCustomizedEvent;
import com.ninsky.cronos.mail.domain.EmailTemplateEvents.EmailTemplateResetEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.UUID;

/**
 * Cross-instance eviction of the email caches (§5.2). The writing instance evicts its own caches after commit; this
 * outbox consumer (crash-safe, retried) then broadcasts the same eviction on a Redis topic that every instance —
 * including the writer — applies to its own caches. Idempotent by {@code eventId}: a redelivered event or a replayed
 * broadcast changes nothing. When Redis is unreachable the eviction is applied locally and the other instances converge
 * within the 30-minute safety TTL.
 */
@Slf4j
@Configuration
public class EmailCacheInvalidator {

    /** What travels on the topic. */
    record Broadcast(UUID eventId, EmailCacheEviction eviction) {
    }

    private final RedissonClient redisson;
    private final EmailContentProvider provider;
    private final ObjectMapper json;
    private final String channel;
    private final Cache<UUID, Boolean> applied = Caffeine.newBuilder().expireAfterWrite(Duration.ofHours(1)).maximumSize(10_000).build();
    private RTopic topic;
    private int listenerId = -1;

    public EmailCacheInvalidator(RedissonClient redisson, EmailContentProvider provider, ObjectMapper json,
                                 @Value("${app.mail.cache-channel:cronos:mail-cache}") String channel) {
        this.redisson = redisson;
        this.provider = provider;
        this.json = json;
        this.channel = channel;
    }

    @Bean
    OutboxHandler<EmailTemplateCustomizedEvent> emailCacheOnCustomized() {
        return OutboxHandlers.of(EmailTemplateCustomizedEvent.class, "EmailCacheInvalidator.customized", (event, envelope) ->
                broadcast(envelope, EmailCacheEviction.content(event.tenantId(), event.templateKey(), event.locale())));
    }

    @Bean
    OutboxHandler<EmailTemplateResetEvent> emailCacheOnReset() {
        return OutboxHandlers.of(EmailTemplateResetEvent.class, "EmailCacheInvalidator.reset", (event, envelope) ->
                broadcast(envelope, EmailCacheEviction.content(event.tenantId(), event.templateKey(), event.locale())));
    }

    @Bean
    OutboxHandler<EmailSettingsChangedEvent> emailCacheOnSettings() {
        return OutboxHandlers.of(EmailSettingsChangedEvent.class, "EmailCacheInvalidator.settings", (event, envelope) ->
                broadcast(envelope, EmailCacheEviction.settings(event.tenantId())));
    }

    @PostConstruct
    void subscribe() {
        try {
            topic = redisson.getTopic(channel, StringCodec.INSTANCE);
            listenerId = topic.addListener(String.class, (name, message) -> receive(message));
        } catch (RuntimeException e) {
            log.warn("Email cache evictions run locally only: cannot subscribe to {} ({})", channel, e.getMessage());
            topic = null;
        }
    }

    @PreDestroy
    void unsubscribe() {
        if (topic != null && listenerId >= 0) {
            try {
                topic.removeListener(listenerId);
            } catch (RuntimeException e) {
                log.debug("Could not remove the email cache listener: {}", e.getMessage());
            }
        }
    }

    /** Sends {@code eviction} to every instance; falls back to this one when Redis is unavailable. */
    void broadcast(EventEnvelope envelope, EmailCacheEviction eviction) {
        Broadcast message = new Broadcast(envelope.id(), eviction);
        if (topic == null) {
            apply(message);
            return;
        }
        try {
            topic.publish(json.writeValueAsString(message));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Email cache eviction cannot be serialised", e);
        } catch (RuntimeException e) {
            log.warn("Email cache fan-out failed, evicting locally: {}", e.getMessage());
            apply(message);
        }
    }

    void receive(String message) {
        try {
            apply(json.readValue(message, Broadcast.class));
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("Dropped a malformed email cache eviction: {}", e.getMessage());
        }
    }

    /** Applies each event's eviction once per instance. */
    void apply(Broadcast message) {
        if (applied.asMap().putIfAbsent(message.eventId(), Boolean.TRUE) == null) {
            provider.evict(message.eviction());
        }
    }
}
