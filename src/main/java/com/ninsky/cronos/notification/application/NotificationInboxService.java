package com.ninsky.cronos.notification.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ninsky.cronos.eventing.application.DomainEventPublisher;
import com.ninsky.cronos.eventing.application.TraceContext;
import com.ninsky.cronos.eventing.application.TraceContexts;
import com.ninsky.cronos.iam.shared.ActorProvider;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.infrastructure.web.paging.CatalogPage;
import com.ninsky.cronos.notification.api.AppNotification;
import com.ninsky.cronos.notification.api.StreamTicket;
import com.ninsky.cronos.notification.api.UnreadCount;
import com.ninsky.cronos.notification.domain.NotificationCategory;
import com.ninsky.cronos.notification.domain.NotificationEvents;
import com.ninsky.cronos.notification.infrastructure.NotificationCustomRepository;
import com.ninsky.cronos.notification.infrastructure.NotificationCustomRepository.NotificationRow;
import com.ninsky.cronos.notification.infrastructure.NotificationProperties;
import com.ninsky.cronos.notification.infrastructure.SseTicketCustomRepository;
import com.ninsky.cronos.notification.infrastructure.stream.SseConnectionRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The caller's inbox (§6.2) and live stream (§6.3). Read-state changes publish {@code notification.read}, whose consumer
 * updates the user's other open tabs.
 */
@Service
public class NotificationInboxService {

    static final int PAGE_MAX = 50;
    static final int TICKET_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final NotificationCustomRepository inbox;
    private final SseTicketCustomRepository tickets;
    private final SseConnectionRegistry connections;
    private final NotificationProperties properties;
    private final ActorProvider actors;
    private final DomainEventPublisher publisher;
    private final ObjectMapper json;
    private final Clock clock;

    public NotificationInboxService(NotificationCustomRepository inbox, SseTicketCustomRepository tickets, SseConnectionRegistry connections,
                                    NotificationProperties properties, ActorProvider actors, DomainEventPublisher publisher, ObjectMapper json,
                                    Clock clock) {
        this.inbox = inbox;
        this.tickets = tickets;
        this.connections = connections;
        this.properties = properties;
        this.actors = actors;
        this.publisher = publisher;
        this.json = json;
        this.clock = clock;
    }

    /** Newest first; {@code size} at most 50. */
    @Transactional(readOnly = true)
    public CatalogPage<AppNotification> page(Integer page, Integer size, boolean unreadOnly, Set<NotificationCategory> categories) {
        UUID user = user();
        int pageNumber = page == null || page < 0 ? 0 : page;
        int pageSize = size == null ? 20 : size;
        if (pageSize < 1 || pageSize > PAGE_MAX) {
            throw ApiException.invalid("size", "api.validation.range", 1, PAGE_MAX);
        }
        Set<NotificationCategory> filter = categories == null ? Set.of() : Set.copyOf(categories);
        long total = inbox.count(user, unreadOnly, filter);
        List<AppNotification> content = inbox.page(user, unreadOnly, filter, pageSize, (long) pageNumber * pageSize).stream()
                .map(NotificationInboxService::view).toList();
        int totalPages = (int) ((total + pageSize - 1) / pageSize);
        return new CatalogPage<>(content, pageNumber, pageSize, total, totalPages, pageNumber + 1 >= totalPages);
    }

    @Transactional(readOnly = true)
    public UnreadCount unreadCount() {
        return unread(user());
    }

    /** Marks {@code ids} read (other users' ids are ignored). */
    @Transactional
    public UnreadCount markRead(Collection<UUID> ids) {
        UUID user = user();
        List<UUID> changed = inbox.markRead(user, ids, clock.instant());
        UnreadCount count = unread(user);
        if (!changed.isEmpty()) {
            publisher.publish(new NotificationEvents.NotificationsRead(user, changed, false, count.total()));
        }
        return count;
    }

    @Transactional
    public UnreadCount markAllRead() {
        UUID user = user();
        int changed = inbox.markAllRead(user, clock.instant());
        UnreadCount count = unread(user);
        if (changed > 0) {
            publisher.publish(new NotificationEvents.NotificationsRead(user, List.of(), true, count.total()));
        }
        return count;
    }

    /** @throws ApiException RESOURCE_NOT_FOUND */
    @Transactional
    public void delete(UUID id) {
        UUID user = user();
        if (!inbox.delete(user, id)) {
            throw ApiException.notFound("notification.notFound");
        }
        publisher.publish(new NotificationEvents.NotificationsRead(user, List.of(id), false, unread(user).total()));
    }

    /** A single-use ticket for {@code GET /notifications/stream}, bound to the caller's session. Redis only, no SQL. */
    public StreamTicket ticket() {
        UUID user = user();
        byte[] raw = new byte[TICKET_BYTES];
        RANDOM.nextBytes(raw);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        Instant expiresAt = clock.instant().plus(properties.ticketTtl());
        tickets.insert(sha256(ticket), user, TraceContexts.current().map(TraceContext::sessionId).orElse(null), properties.ticketTtl());
        return new StreamTicket(ticket, expiresAt);
    }

    /**
     * Consumes {@code ticket} and opens the stream: the reconnect delay, the notifications after {@code lastEventId}
     * (at most 100), then live frames.
     *
     * Not transactional: the ticket lives in Redis and the replay is at most two autocommit reads, so no connection is
     * held once the emitter is returned (the stream itself never touches JPA; frames arrive from outbox consumers).
     *
     * @return empty when the ticket is unknown, used or expired
     */
    public Optional<SseEmitter> open(String ticket, String lastEventId) {
        if (ticket == null || ticket.isBlank() || ticket.length() > 100) {
            return Optional.empty();
        }
        return tickets.consume(sha256(ticket)).map(owner -> {
            SseEmitter emitter = connections.open(owner.userId(), owner.sessionId(), properties.streamTimeout());
            SseConnectionRegistry.send(emitter, SseEmitter.event().reconnectTime(5000).comment("connected"));
            parse(lastEventId).ifPresent(after -> {
                List<NotificationRow> missed = inbox.after(owner.userId(), after, properties.replayLimit());
                long unread = unread(owner.userId()).total();
                missed.forEach(row -> SseConnectionRegistry.send(emitter, SseEmitter.event().id(row.id().toString()).name("notification")
                        .data(notificationFrame(view(row), unread))));
            });
            return emitter;
        });
    }

    /** Body of a {@code notification} frame. */
    public String notificationFrame(AppNotification notification, long unread) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("notification", notification);
        body.put("unread", unread);
        return write(body);
    }

    public String write(Object body) {
        try {
            return json.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stream frame cannot be serialised", e);
        }
    }

    public UnreadCount unread(UUID user) {
        Map<NotificationCategory, Long> byCategory = inbox.unreadByCategory(user);
        return new UnreadCount(byCategory.values().stream().mapToLong(Long::longValue).sum(), byCategory);
    }

    public static AppNotification view(NotificationRow row) {
        return new AppNotification(row.id(), row.type(), row.category(), row.severity(), row.title(), row.body(), row.link(), row.subjectType(),
                row.subjectId(), row.actorName(), row.createdAt(), row.readAt());
    }

    private UUID user() {
        return actors.require().id();
    }

    private static Optional<UUID> parse(String lastEventId) {
        if (lastEventId == null || lastEventId.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(lastEventId.trim()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
