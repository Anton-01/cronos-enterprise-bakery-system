package com.ninsky.cronos.notification.infrastructure.stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ninsky.cronos.notification.infrastructure.NotificationProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Fans stream frames out to every instance through Redis pub/sub (§6.3): the user may be connected to any node, never
 * assume it is the one that handled the command. When Redis cannot be reached the frame is still delivered locally.
 */
@Slf4j
@Component
public class StreamBroadcaster {

    private final RedissonClient redisson;
    private final SseConnectionRegistry registry;
    private final ObjectMapper json;
    private final NotificationProperties properties;
    private RTopic topic;
    private int listenerId = -1;

    public StreamBroadcaster(RedissonClient redisson, SseConnectionRegistry registry, ObjectMapper json, NotificationProperties properties) {
        this.redisson = redisson;
        this.registry = registry;
        this.json = json;
        this.properties = properties;
    }

    @PostConstruct
    void subscribe() {
        try {
            topic = redisson.getTopic(properties.streamChannel(), StringCodec.INSTANCE);
            listenerId = topic.addListener(String.class, (channel, message) -> receive(message));
        } catch (RuntimeException e) {
            log.warn("Stream fan-out runs locally only: cannot subscribe to {} ({})", properties.streamChannel(), e.getMessage());
            topic = null;
        }
    }

    @PreDestroy
    void unsubscribe() {
        if (topic != null && listenerId >= 0) {
            try {
                topic.removeListener(listenerId);
            } catch (RuntimeException e) {
                log.debug("Could not remove the stream listener: {}", e.getMessage());
            }
        }
    }

    /** Sends {@code frame} to the user's connections on every instance. */
    public void publish(StreamFrame frame) {
        if (topic == null) {
            registry.deliver(frame);
            return;
        }
        try {
            topic.publish(json.writeValueAsString(frame));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stream frame cannot be serialised", e);
        } catch (RuntimeException e) {
            log.warn("Stream fan-out failed, delivering locally: {}", e.getMessage());
            registry.deliver(frame);
        }
    }

    /** Ends the streams of a session (or every stream of the user when {@code sessionId} is null) on every instance. */
    public void close(UUID userId, UUID sessionId) {
        publish(StreamFrame.close(userId, sessionId));
    }

    private void receive(String message) {
        try {
            registry.deliver(json.readValue(message, StreamFrame.class));
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("Dropped a malformed stream frame: {}", e.getMessage());
        }
    }
}
