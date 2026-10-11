package com.ninsky.cronos.notification.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ninsky.cronos.eventing.application.DomainEventPublisher;
import com.ninsky.cronos.eventing.application.EventEnvelope;
import com.ninsky.cronos.eventing.domain.TimeOrderedIds;
import com.ninsky.cronos.iam.shared.ActorProvider;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.infrastructure.util.auth.RequestContextUtil;
import com.ninsky.cronos.notification.api.PushConfig;
import com.ninsky.cronos.notification.api.PushDevice;
import com.ninsky.cronos.notification.api.PushSubscriptionRequest;
import com.ninsky.cronos.notification.api.PushTestResult;
import com.ninsky.cronos.notification.domain.NotificationCategory;
import com.ninsky.cronos.notification.domain.NotificationEvents;
import com.ninsky.cronos.notification.domain.NotificationSeverity;
import com.ninsky.cronos.notification.domain.NotificationType;
import com.ninsky.cronos.notification.infrastructure.NotificationProperties;
import com.ninsky.cronos.notification.infrastructure.PushSubscriptionCustomRepository;
import com.ninsky.cronos.notification.infrastructure.PushSubscriptionCustomRepository.DeliveryStatus;
import com.ninsky.cronos.notification.infrastructure.PushSubscriptionCustomRepository.SubscriptionRow;
import com.ninsky.cronos.notification.infrastructure.push.WebPushCrypto;
import com.ninsky.cronos.notification.infrastructure.push.WebPushGateway;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

/**
 * Web Push devices of the caller and delivery of {@code notification.created} to them (§6.4). A delivery that fails
 * with a retryable answer (429, 5xx, no answer) makes the outbox retry the event; devices already settled for the
 * event are skipped, so a retry never pushes twice.
 */
@Service
public class PushService {

    static final Duration SECURITY_TTL = Duration.ofHours(1);
    static final int TOPIC_MAX = 32;
    private static final Semaphore IN_FLIGHT = new Semaphore(50);

    /** Raised to make the outbox retry the deliveries that can still succeed. */
    public static final class RetryablePushFailure extends RuntimeException {
        public RetryablePushFailure(String message) {
            super(message);
        }
    }

    private record Outcome(SubscriptionRow subscription, WebPushGateway.Result result) {
    }

    private final PushSubscriptionCustomRepository subscriptions;
    private final WebPushGateway gateway;
    private final NotificationProperties properties;
    private final ActorProvider actors;
    private final DomainEventPublisher publisher;
    private final ObjectMapper json;
    private final MeterRegistry meters;
    private final RequestContextUtil requestContext;
    private final TransactionTemplate independent;
    private final Clock clock;

    public PushService(PushSubscriptionCustomRepository subscriptions, WebPushGateway gateway, NotificationProperties properties,
                       ActorProvider actors, DomainEventPublisher publisher, ObjectMapper json, MeterRegistry meters,
                       RequestContextUtil requestContext, PlatformTransactionManager transactions, Clock clock) {
        this.subscriptions = subscriptions;
        this.gateway = gateway;
        this.properties = properties;
        this.actors = actors;
        this.publisher = publisher;
        this.json = json;
        this.meters = meters;
        this.requestContext = requestContext;
        this.independent = new TransactionTemplate(transactions);
        this.independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    public PushConfig config() {
        return new PushConfig(gateway.enabled(), gateway.publicKey());
    }

    @Transactional(readOnly = true)
    public List<PushDevice> devices() {
        return subscriptions.active(actors.require().id()).stream().map(PushService::device).toList();
    }

    /**
     * Registers (or refreshes) the caller's browser; the oldest devices beyond 10 are revoked.
     *
     * @throws ApiException VALIDATION_ERROR on {@code endpoint} outside the browsers' push services or malformed keys
     */
    @Transactional
    public PushDevice subscribe(PushSubscriptionRequest request) {
        UUID user = actors.require().id();
        validate(request, "");
        Instant now = clock.instant();
        UUID id = subscriptions.upsert(new SubscriptionRow(TimeOrderedIds.next(clock), user, request.endpoint().trim(), request.keys().p256dh(),
                request.keys().auth(), blankToNull(request.deviceLabel()), cut(requestContext.getBrowser(), 40), cut(requestContext.getOperatingSystem(), 40),
                cut(requestContext.getUserAgent(), 512),
                request.locale(), now, null, 0, null), sha256(request.endpoint().trim()));
        subscriptions.revokeBeyond(user, properties.maxDevicesPerUser(), now);
        return subscriptions.findActive(user, id).map(PushService::device)
                .orElseThrow(() -> ApiException.notFound("notification.device.notFound"));
    }

    @Transactional
    public void unsubscribe(String endpoint) {
        subscriptions.revokeByEndpoint(actors.require().id(), endpoint.trim(), clock.instant());
    }

    /** @throws ApiException RESOURCE_NOT_FOUND */
    @Transactional
    public void delete(UUID id) {
        UUID user = actors.require().id();
        SubscriptionRow row = subscriptions.findActive(user, id).orElseThrow(() -> ApiException.notFound("notification.device.notFound"));
        subscriptions.revoke(row.id(), clock.instant());
    }

    /**
     * The service worker's renewal: replaces the subscription of {@code oldEndpoint} only when it is still active.
     *
     * @return false when nothing matched (answered 404 without detail)
     */
    @Transactional
    public boolean renew(String oldEndpoint, PushSubscriptionRequest subscription) {
        validate(subscription, "subscription.");
        return subscriptions.findActiveByEndpoint(oldEndpoint.trim()).map(row -> {
            String endpoint = subscription.endpoint().trim();
            if (!endpoint.equals(row.endpoint())) {
                subscriptions.findActiveByEndpoint(endpoint).ifPresent(other -> subscriptions.revoke(other.id(), clock.instant()));
            }
            subscriptions.renew(row.id(), endpoint, sha256(endpoint), subscription.keys().p256dh(), subscription.keys().auth());
            return true;
        }).orElse(false);
    }

    /** Sends a test push to one device or all of the caller's devices, synchronously (not stored in the inbox). */
    @Transactional
    public PushTestResult test(UUID deviceId, String title, String body) {
        if (!gateway.enabled()) {
            throw ApiException.of(com.ninsky.cronos.infrastructure.exception.ApiErrorCode.VALIDATION_ERROR, null, "notification.push.disabled");
        }
        UUID user = actors.require().id();
        List<SubscriptionRow> targets = deviceId == null ? subscriptions.active(user)
                : List.of(subscriptions.findActive(user, deviceId).orElseThrow(() -> ApiException.notFound("notification.device.notFound")));
        UUID id = UUID.randomUUID();
        byte[] payload = payload(id, NotificationType.SYSTEM_ANNOUNCEMENT, title, body, "/cronos", "test:" + id, NotificationSeverity.INFO);
        List<Outcome> outcomes = send(targets, payload, properties.pushDefaultTtl(), WebPushGateway.Urgency.NORMAL, null);
        Map<Boolean, Long> split = outcomes.stream().collect(Collectors.partitioningBy(outcome -> outcome.result().delivered(), Collectors.counting()));
        outcomes.forEach(outcome -> settle(outcome, null, null, false));
        return new PushTestResult(split.get(true).intValue(), split.get(false).intValue());
    }

    /**
     * Pushes a created notification to the recipient's devices (outbox consumer).
     *
     * @throws RetryablePushFailure when a device answered 429/5xx or did not answer
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void deliver(NotificationEvents.NotificationCreated event, EventEnvelope envelope) {
        if (!event.push() || !gateway.enabled()) {
            return;
        }
        Set<UUID> settled = subscriptions.settled(envelope.id());
        List<SubscriptionRow> targets = subscriptions.active(event.userId()).stream().filter(row -> !settled.contains(row.id())).toList();
        if (targets.isEmpty()) {
            return;
        }
        NotificationCategory category = event.type().category();
        String tag = event.subjectType() != null && event.subjectId() != null
                ? event.subjectType().toLowerCase(Locale.ROOT) + ":" + event.subjectId()
                : event.type().name().toLowerCase(Locale.ROOT) + ":" + event.notificationId();
        byte[] payload = payload(event.notificationId(), event.type(), event.title(), event.body(), event.link(), tag, event.severity());
        Duration ttl = category == NotificationCategory.SECURITY ? SECURITY_TTL : properties.pushDefaultTtl();
        WebPushGateway.Urgency urgency = event.severity() == NotificationSeverity.CRITICAL || category == NotificationCategory.SECURITY
                ? WebPushGateway.Urgency.HIGH
                : category == NotificationCategory.SYSTEM ? WebPushGateway.Urgency.LOW : WebPushGateway.Urgency.NORMAL;

        List<Outcome> outcomes = send(targets, payload, ttl, urgency, topic(tag));
        Map<Boolean, List<Outcome>> split = outcomes.stream().collect(Collectors.partitioningBy(outcome -> outcome.result().delivered()));
        meters.counter("cronos.push.deliveries", "result", "delivered").increment(split.get(true).size());
        meters.counter("cronos.push.deliveries", "result", "failed").increment(split.get(false).size());
        // Outcomes commit on their own: the retry that a failure triggers must still see the devices already reached.
        Long retryable = independent.execute(status -> outcomes.stream()
                .filter(outcome -> settle(outcome, envelope, event.notificationId(), true)).count());
        if (retryable != null && retryable > 0) {
            throw new RetryablePushFailure(retryable + " device(s) could not be reached yet");
        }
    }

    /** Records an outcome; returns true when it deserves a retry. */
    private boolean settle(Outcome outcome, EventEnvelope envelope, UUID notificationId, boolean record) {
        SubscriptionRow subscription = outcome.subscription();
        WebPushGateway.Result result = outcome.result();
        Instant now = clock.instant();
        DeliveryStatus status;
        boolean retry = false;
        if (result.delivered()) {
            subscriptions.succeeded(subscription.id(), now);
            status = DeliveryStatus.DELIVERED;
        } else if (result.gone()) {
            subscriptions.revoke(subscription.id(), now);
            status = DeliveryStatus.REVOKED;
        } else if (result.permanent()) {
            status = DeliveryStatus.FAILED;
        } else if (subscriptions.failed(subscription.id()) >= properties.maxConsecutiveFailures()) {
            subscriptions.revoke(subscription.id(), now);
            status = DeliveryStatus.REVOKED;
        } else {
            status = DeliveryStatus.RETRYING;
            retry = true;
        }
        if (record && envelope != null) {
            subscriptions.recordDelivery(TimeOrderedIds.next(clock), envelope.id(), subscription.id(), notificationId, status,
                    result.status() == 0 ? null : result.status(), result.error(), now, MDC.get("traceId"));
            if (status == DeliveryStatus.REVOKED || status == DeliveryStatus.FAILED) {
                publisher.publish(new NotificationEvents.PushDeliveryFailed(subscription.id(), subscription.userId(), notificationId,
                        result.status() == 0 ? null : result.status(), status == DeliveryStatus.REVOKED));
            }
        }
        return retry;
    }

    /** Sends in parallel on virtual threads, at most 50 requests in flight across the instance. */
    private List<Outcome> send(List<SubscriptionRow> targets, byte[] payload, Duration ttl, WebPushGateway.Urgency urgency, String topic) {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Outcome>> futures = targets.stream().map(target -> executor.submit(() -> {
                IN_FLIGHT.acquire();
                try {
                    return new Outcome(target, gateway.send(new WebPushGateway.Target(target.endpoint(), target.p256dh(), target.auth()), payload,
                            ttl, urgency, topic));
                } finally {
                    IN_FLIGHT.release();
                }
            })).toList();
            return futures.stream().map(future -> {
                try {
                    return future.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RetryablePushFailure("Interrupted while pushing");
                } catch (java.util.concurrent.ExecutionException e) {
                    throw new RetryablePushFailure("Push failed: " + e.getCause());
                }
            }).toList();
        }
    }

    private byte[] payload(UUID id, NotificationType type, String title, String body, String link, String tag, NotificationSeverity severity) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("notificationId", id);
        payload.put("type", type);
        payload.put("title", title);
        payload.put("body", body);
        payload.put("link", link);
        payload.put("tag", tag);
        payload.put("severity", severity);
        payload.put("timestamp", clock.instant().toString());
        payload.put("renotify", false);
        try {
            byte[] bytes = json.writeValueAsBytes(payload);
            if (bytes.length > 3000) {
                payload.put("body", body == null ? null : body.substring(0, Math.min(body.length(), 300)) + "…");
                bytes = json.writeValueAsBytes(payload);
            }
            return bytes;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Push payload cannot be serialised", e);
        }
    }

    /** Endpoint on a browser push service (https, allowed host) and well-formed keys. */
    private void validate(PushSubscriptionRequest request, String prefix) {
        URI endpoint;
        try {
            endpoint = URI.create(request.endpoint().trim());
        } catch (IllegalArgumentException e) {
            throw ApiException.invalid(prefix + "endpoint", "notification.push.endpointInvalid");
        }
        String host = endpoint.getHost() == null ? "" : endpoint.getHost().toLowerCase(Locale.ROOT);
        boolean allowed = "https".equalsIgnoreCase(endpoint.getScheme()) && endpoint.getUserInfo() == null
                && properties.pushAllowedHosts().stream().map(suffix -> suffix.toLowerCase(Locale.ROOT))
                .anyMatch(suffix -> host.equals(suffix) || host.endsWith("." + suffix));
        if (!allowed) {
            throw ApiException.invalid(prefix + "endpoint", "notification.push.endpointInvalid");
        }
        try {
            WebPushCrypto.publicKey(WebPushCrypto.base64Url(request.keys().p256dh()));
            if (WebPushCrypto.base64Url(request.keys().auth()).length != 16) {
                throw new IllegalArgumentException("auth");
            }
        } catch (java.security.GeneralSecurityException | IllegalArgumentException e) {
            throw ApiException.invalid(prefix + "keys", "notification.push.keysInvalid");
        }
    }

    private static PushDevice device(SubscriptionRow row) {
        return new PushDevice(row.id(), row.deviceLabel(), row.browser(), row.os(), row.locale(), row.createdAt(), row.lastSuccessAt(),
                row.failureCount());
    }

    /** {@code tag} hashed to at most 32 URL-safe characters (the Topic header). */
    static String topic(String tag) {
        return WebPushCrypto.base64Url(sha256Bytes(tag)).substring(0, TOPIC_MAX);
    }

    private static String sha256(String value) {
        return java.util.HexFormat.of().formatHex(sha256Bytes(value));
    }

    private static byte[] sha256Bytes(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
