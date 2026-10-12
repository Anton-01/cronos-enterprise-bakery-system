package com.ninsky.cronos.eventing.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * An event with everything around it: the chain of causes up to the request ({@code causes}, root first), the
 * events its handlers published ({@code effects}, as a tree) and the deliveries it produced.
 */
public record EventTrail(EventTrailEntry event, List<EventTrailEntry> causes, List<EffectNode> effects,
                         List<Delivery> deliveries) {

    public EventTrail {
        causes = List.copyOf(causes);
        effects = List.copyOf(effects);
        deliveries = List.copyOf(deliveries);
    }

    /** An effect and its own effects. */
    public record EffectNode(EventTrailEntry event, List<EffectNode> effects) {
        public EffectNode {
            effects = List.copyOf(effects);
        }
    }

    /**
     * An email or push produced by an event of the trail.
     *
     * @param channel {@code EMAIL} or {@code PUSH}
     * @param detail  template key (email) or browser/os (push)
     */
    public record Delivery(UUID id, UUID eventId, String channel, String detail, String status, int attempts,
                           String lastError, Instant createdAt, Instant completedAt) {
    }
}
