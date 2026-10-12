package com.ninsky.cronos.eventing.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ninsky.cronos.eventing.application.EventEnvelope;
import com.ninsky.cronos.eventing.application.OutboxHandler;
import com.ninsky.cronos.eventing.application.TraceContexts;
import com.ninsky.cronos.eventing.domain.DomainEvent;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Delivers one claimed outbox message to every {@link OutboxHandler} of its type. Each handler runs in its own
 * transaction together with its {@code processed_events} mark, under the message's trace context (so events it
 * publishes are recorded as effects of this one). The first failure stops the delivery and schedules a retry
 * following the type's {@link com.ninsky.cronos.eventing.domain.RetrySchedule}; handlers that already applied the
 * message are skipped on the retry.
 */
@Slf4j
@Component
public class OutboxDispatcher {

    private final OutboxCustomRepository outbox;
    private final EventTypeRegistry types;
    private final SensitivePayloadCodec sensitive;
    private final ObjectMapper json;
    private final Map<String, List<OutboxHandler<?>>> handlersByType;
    private final TransactionTemplate perHandler;
    private final MeterRegistry meters;
    private final Clock clock;

    public OutboxDispatcher(OutboxCustomRepository outbox, EventTypeRegistry types, SensitivePayloadCodec sensitive,
                            ObjectMapper json, ObjectProvider<OutboxHandler<?>> handlers, PlatformTransactionManager transactions,
                            MeterRegistry meters, Clock clock) {
        this.outbox = outbox;
        this.types = types;
        this.sensitive = sensitive;
        this.json = json;
        this.handlersByType = index(types, handlers.orderedStream().toList());
        this.perHandler = new TransactionTemplate(transactions);
        this.perHandler.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.meters = meters;
        this.clock = clock;
    }

    /** Handler names per event type (startup diagnostics and tests). */
    public Map<String, List<String>> handlerNames() {
        return handlersByType.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey,
                entry -> entry.getValue().stream().map(OutboxHandler::name).toList()));
    }

    /** Delivers {@code message}; never throws (failures are recorded on the message). */
    public void dispatch(OutboxMessage message) {
        Optional<EventTypeRegistry.Registration> registration = types.byType(message.type());
        if (registration.isEmpty()) {
            fail(message, null, "Unknown event type " + message.type(), true);
            return;
        }
        List<OutboxHandler<?>> handlers = handlersByType.getOrDefault(message.type(), List.of());
        if (handlers.isEmpty()) {
            outbox.markDispatched(message.id(), clock.instant());
            count(message.type(), "no_handler");
            return;
        }
        DomainEvent event;
        try {
            event = read(message, registration.get());
        } catch (RuntimeException e) {
            fail(message, registration.get(), "Unreadable payload: " + e.getMessage(), true);
            return;
        }
        EventEnvelope envelope = new EventEnvelope(message.id(), message.type(), message.aggregateType(), message.aggregateId(),
                message.correlationId(), message.causationId(), message.traceId(), message.actorId(), message.occurredAt(),
                message.attempts());
        for (OutboxHandler<?> handler : handlers) {
            try {
                TraceContexts.runWith(envelope.traceContext(), () -> perHandler.executeWithoutResult(status -> apply(handler, event, envelope)));
            } catch (RuntimeException e) {
                log.warn("Handler {} failed on {} {} (attempt {}): {}", handler.name(), message.type(), message.id(),
                        message.attempts() + 1, e.toString());
                fail(message, registration.get(), handler.name() + ": " + e, false);
                return;
            }
        }
        outbox.markDispatched(message.id(), clock.instant());
        count(message.type(), "dispatched");
    }

    @SuppressWarnings("unchecked") // handlers are indexed by the type registered for their own eventClass()
    private void apply(OutboxHandler<?> handler, DomainEvent event, EventEnvelope envelope) {
        if (outbox.markProcessed(handler.name(), envelope.id(), clock.instant())) {
            ((OutboxHandler<DomainEvent>) handler).handle(event, envelope);
        }
    }

    private DomainEvent read(OutboxMessage message, EventTypeRegistry.Registration registration) {
        try {
            JsonNode tree = json.readTree(message.payload());
            if (!(tree instanceof ObjectNode payload)) {
                throw new IllegalStateException("payload is not a JSON object");
            }
            return json.treeToValue(sensitive.open(payload, registration.sensitiveComponents()), registration.eventClass());
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e.getOriginalMessage(), e);
        }
    }

    private void fail(OutboxMessage message, EventTypeRegistry.Registration registration, String error, boolean poison) {
        int attempts = message.attempts() + 1;
        boolean dead = poison || registration == null || registration.retry().exhausted(attempts);
        Instant now = clock.instant();
        Instant next = dead ? now : now.plus(registration.retry().delayAfter(attempts));
        outbox.markFailed(message.id(), attempts, next, error, dead);
        count(message.type(), dead ? "dead" : "retry");
        if (dead) {
            log.error("Outbox message {} ({}) is DEAD after {} attempt(s): {}", message.id(), message.type(), attempts, error);
        }
    }

    private void count(String type, String outcome) {
        meters.counter("cronos.outbox.dispatch", "type", type, "outcome", outcome).increment();
    }

    private static Map<String, List<OutboxHandler<?>>> index(EventTypeRegistry types, List<OutboxHandler<?>> handlers) {
        Set<String> duplicates = handlers.stream()
                .collect(Collectors.groupingBy(OutboxHandler::name, Collectors.counting()))
                .entrySet().stream().filter(entry -> entry.getValue() > 1).map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
        if (!duplicates.isEmpty()) {
            throw new IllegalStateException("Outbox handler names must be unique: " + duplicates);
        }
        return handlers.stream().collect(Collectors.collectingAndThen(
                Collectors.groupingBy(handler -> types.registrationOf(handler.eventClass()).type(), Collectors.toList()),
                grouped -> grouped.entrySet().stream()
                        .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue())))));
    }
}
