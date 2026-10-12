package com.ninsky.cronos.eventing.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ninsky.cronos.eventing.application.DomainEventPublisher;
import com.ninsky.cronos.eventing.application.PublishOptions;
import com.ninsky.cronos.eventing.application.TraceContext;
import com.ninsky.cronos.eventing.application.TraceContexts;
import com.ninsky.cronos.eventing.domain.DomainEvent;
import com.ninsky.cronos.eventing.domain.TimeOrderedIds;
import com.ninsky.cronos.iam.shared.Actor;
import com.ninsky.cronos.iam.shared.ActorProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Writes domain events to {@code outbox_messages} inside the caller's transaction, stamping the trace metadata
 * of the current {@link TraceContext} (correlation, causation, trace id, actor). After the transaction commits
 * the relays are woken so side effects start within milliseconds instead of waiting for the next poll.
 */
@Slf4j
@Component
public class OutboxDomainEventPublisher implements DomainEventPublisher {

    private final OutboxCustomRepository outbox;
    private final EventTypeRegistry types;
    private final SensitivePayloadCodec sensitive;
    private final ObjectMapper json;
    private final ActorProvider actors;
    private final OutboxSignal signal;
    private final Clock clock;

    public OutboxDomainEventPublisher(OutboxCustomRepository outbox, EventTypeRegistry types, SensitivePayloadCodec sensitive,
                                      ObjectMapper json, ActorProvider actors, OutboxSignal signal, Clock clock) {
        this.outbox = outbox;
        this.types = types;
        this.sensitive = sensitive;
        this.json = json;
        this.actors = actors;
        this.signal = signal;
        this.clock = clock;
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException when the event class is not annotated with {@code @EventType}
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean publish(DomainEvent event, PublishOptions options) {
        // Checked explicitly too: the interface's default publish(event) reaches this method without the proxy.
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalTransactionStateException("Domain events can only be published inside a transaction");
        }
        EventTypeRegistry.Registration registration = types.registrationOf(event);
        TraceContext trace = TraceContexts.currentOrNew(clock);
        UUID actorId = trace.actorId() != null ? trace.actorId() : actors.current().map(Actor::id).orElse(null);
        Instant now = clock.instant();
        ObjectNode payload = sensitive.seal(json.valueToTree(event), registration.sensitiveComponents());
        OutboxMessage message = new OutboxMessage(TimeOrderedIds.next(clock), registration.type(), event.aggregateType(),
                event.aggregateId(), payload.toString(), trace.correlationId(), trace.causationId(), trace.traceId(), actorId,
                now, options.availableAt() != null && options.availableAt().isAfter(now) ? options.availableAt() : now,
                OutboxStatus.PENDING, 0, null, null, options.dedupeKey());
        boolean inserted = outbox.insert(message);
        if (!inserted) {
            log.debug("Event {} coalesced by dedupe key {}", registration.type(), options.dedupeKey());
            return false;
        }
        wakeRelaysAfterCommit();
        return true;
    }

    private void wakeRelaysAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    signal.committed();
                }
            });
        }
    }
}
