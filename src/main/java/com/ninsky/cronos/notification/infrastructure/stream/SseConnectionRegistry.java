package com.ninsky.cronos.notification.infrastructure.stream;

import com.ninsky.cronos.notification.infrastructure.NotificationProperties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The open SSE connections of every user on this instance (§6.3). Thread-safe; a connection removes itself on
 * completion, timeout or error, and is completed when its session is revoked or locked. A comment line every 25 s
 * keeps proxies from closing idle streams.
 */
@Slf4j
@Component
public class SseConnectionRegistry {

    /** One open stream and the session it belongs to. */
    record Connection(SseEmitter emitter, UUID sessionId) {
    }

    private final ConcurrentMap<UUID, Set<Connection>> byUser = new ConcurrentHashMap<>();
    private final NotificationProperties properties;
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform()
            .name("sse-heartbeat").daemon(true).factory());

    public SseConnectionRegistry(NotificationProperties properties, MeterRegistry meters) {
        this.properties = properties;
        Gauge.builder("cronos.sse.connections", byUser, map -> map.values().stream().mapToInt(Set::size).sum()).register(meters);
    }

    @PostConstruct
    void startHeartbeat() {
        long period = properties.heartbeat().toMillis();
        heartbeat.scheduleAtFixedRate(this::beat, period, period, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void shutdown() {
        heartbeat.shutdownNow();
        byUser.values().forEach(connections -> connections.forEach(connection -> connection.emitter().complete()));
        byUser.clear();
    }

    /** Opens a stream of {@code userId} bound to {@code sessionId}. */
    public SseEmitter open(UUID userId, UUID sessionId, Duration timeout) {
        SseEmitter emitter = new SseEmitter(timeout.toMillis());
        Connection connection = new Connection(emitter, sessionId);
        byUser.computeIfAbsent(userId, id -> ConcurrentHashMap.newKeySet()).add(connection);
        Runnable remove = () -> byUser.computeIfPresent(userId, (id, set) -> {
            set.remove(connection);
            return set.isEmpty() ? null : set;
        });
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(error -> remove.run());
        return emitter;
    }

    /** Applies a frame to this instance's connections of its user (best effort). */
    public void deliver(StreamFrame frame) {
        Set<Connection> connections = byUser.getOrDefault(frame.userId(), Set.of());
        if (frame.kind() == StreamFrame.Kind.CLOSE) {
            connections.stream()
                    .filter(connection -> frame.sessionId() == null || Objects.equals(frame.sessionId(), connection.sessionId()))
                    .toList()
                    .forEach(connection -> connection.emitter().complete());
            return;
        }
        SseEmitter.SseEventBuilder event = SseEmitter.event().name(frame.event()).data(frame.data());
        if (frame.id() != null) {
            event.id(frame.id());
        }
        List.copyOf(connections).forEach(connection -> send(connection.emitter(), event));
    }

    /** Sends {@code event} to one stream; a broken stream is completed (and removes itself). */
    public static void send(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException e) {
            emitter.completeWithError(e);
        }
    }

    int connections() {
        return byUser.values().stream().mapToInt(Set::size).sum();
    }

    private void beat() {
        try {
            byUser.values().forEach(connections -> List.copyOf(connections)
                    .forEach(connection -> send(connection.emitter(), SseEmitter.event().comment("heartbeat"))));
        } catch (RuntimeException e) {
            log.debug("Heartbeat round failed: {}", e.getMessage());
        }
    }
}
