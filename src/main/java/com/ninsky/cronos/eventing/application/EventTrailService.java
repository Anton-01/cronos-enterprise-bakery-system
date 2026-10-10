package com.ninsky.cronos.eventing.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ninsky.cronos.eventing.api.EventTrail;
import com.ninsky.cronos.eventing.api.EventTrailEntry;
import com.ninsky.cronos.eventing.api.EventTrailFilter;
import com.ninsky.cronos.eventing.infrastructure.EventTrailCustomRepository;
import com.ninsky.cronos.eventing.infrastructure.EventTypeRegistry;
import com.ninsky.cronos.eventing.infrastructure.OutboxCustomRepository;
import com.ninsky.cronos.eventing.infrastructure.OutboxMessage;
import com.ninsky.cronos.eventing.infrastructure.OutboxSignal;
import com.ninsky.cronos.eventing.infrastructure.OutboxStatus;
import com.ninsky.cronos.eventing.infrastructure.SensitivePayloadCodec;
import com.ninsky.cronos.iam.shared.Actor;
import com.ninsky.cronos.iam.shared.UserDirectory;
import com.ninsky.cronos.iam.shared.UserRef;
import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.infrastructure.web.paging.CatalogPage;
import com.ninsky.cronos.infrastructure.web.paging.PageQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Administrator view of the event log (§8.6): search, the cause/effect tree of one event and re-queueing of
 * dead messages. Payloads are always redacted.
 */
@Service
public class EventTrailService {

    private static final Set<String> STATUSES = Set.of("PENDING", "DISPATCHED", "FAILED", "DEAD");

    private final EventTrailCustomRepository trail;
    private final OutboxCustomRepository outbox;
    private final EventTypeRegistry types;
    private final SensitivePayloadCodec sensitive;
    private final UserDirectory users;
    private final OutboxSignal signal;
    private final ObjectMapper json;
    private final Clock clock;

    public EventTrailService(EventTrailCustomRepository trail, OutboxCustomRepository outbox, EventTypeRegistry types,
                             SensitivePayloadCodec sensitive, UserDirectory users, OutboxSignal signal, ObjectMapper json, Clock clock) {
        this.trail = trail;
        this.outbox = outbox;
        this.types = types;
        this.sensitive = sensitive;
        this.users = users;
        this.signal = signal;
        this.json = json;
        this.clock = clock;
    }

    /**
     * Messages matching {@code filter}, newest first, without payloads.
     *
     * @throws ApiException VALIDATION_ERROR on an unknown status or an inverted range
     */
    @Transactional(readOnly = true)
    public CatalogPage<EventTrailEntry> search(EventTrailFilter filter, Integer page, Integer size) {
        if (filter.status() != null && !STATUSES.contains(filter.status())) {
            throw ApiException.invalid("status", "events.status.unknown", filter.status());
        }
        if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
            throw ApiException.invalid("to", "events.range.inverted");
        }
        PageQuery query = PageQuery.of(page, size, null, Map.of("occurredAt", "id"), "occurredAt,desc");
        List<OutboxMessage> rows = trail.search(filter, query.size(), query.offset());
        return CatalogPage.of(entries(rows, false), query, trail.count(filter));
    }

    /**
     * The event, its causes up to the originating request, its effects as a tree and the deliveries of all of them.
     *
     * @throws ApiException RESOURCE_NOT_FOUND when the event does not exist (or was purged)
     */
    @Transactional(readOnly = true)
    public EventTrail trail(UUID id) {
        List<OutboxMessage> chain = trail.causeChain(id);
        if (chain.isEmpty()) {
            throw ApiException.notFound("events.notFound");
        }
        List<OutboxMessage> effects = trail.effects(id);
        List<EventTrailEntry> chainEntries = entries(chain, true);
        List<EventTrailEntry> effectEntries = entries(effects, true);
        Map<UUID, List<EventTrailEntry>> children = effectEntries.stream()
                .collect(Collectors.groupingBy(EventTrailEntry::causationId, Collectors.toUnmodifiableList()));
        List<UUID> involved = Stream.concat(Stream.of(id), effects.stream().map(OutboxMessage::id)).toList();
        return new EventTrail(chainEntries.getLast(), chainEntries.subList(0, chainEntries.size() - 1),
                tree(id, children), trail.deliveries(involved));
    }

    /**
     * Re-queues a DEAD message with a fresh attempt budget and wakes the relay.
     *
     * @throws ApiException ACCESS_DENIED unless the caller is SUPER_ADMIN; RESOURCE_NOT_FOUND; INVALID_STATE_TRANSITION
     *                      when the message is not DEAD
     */
    @Transactional
    public EventTrailEntry retry(UUID id, Actor actor) {
        if (!actor.superAdmin()) {
            throw ApiException.of(ApiErrorCode.ACCESS_DENIED, null, "events.retry.superAdminOnly");
        }
        OutboxMessage message = outbox.find(id).orElseThrow(() -> ApiException.notFound("events.notFound"));
        if (message.status() != OutboxStatus.DEAD || !outbox.requeueDead(id, clock.instant())) {
            throw ApiException.withDetails(ApiErrorCode.INVALID_STATE_TRANSITION, "status",
                    Map.of("from", message.status().name(), "to", OutboxStatus.PENDING.name()), "events.retry.notDead");
        }
        signal.committed();
        return entries(List.of(outbox.find(id).orElseThrow()), false).getFirst();
    }

    private List<EventTrail.EffectNode> tree(UUID parent, Map<UUID, List<EventTrailEntry>> children) {
        return children.getOrDefault(parent, List.of()).stream()
                .map(child -> new EventTrail.EffectNode(child, tree(child.id(), children)))
                .toList();
    }

    private List<EventTrailEntry> entries(List<OutboxMessage> rows, boolean withPayload) {
        Collection<UUID> ids = rows.stream().map(OutboxMessage::id).toList();
        Map<UUID, List<EventTrailCustomRepository.HandlerMark>> handlers = trail.handlers(ids);
        Map<UUID, UserRef> actors = users.refs(rows.stream().map(OutboxMessage::actorId).toList());
        List<EventTrailEntry> entries = new ArrayList<>(rows.size());
        for (OutboxMessage row : rows) {
            entries.add(new EventTrailEntry(row.id(), row.type(), row.aggregateType(), row.aggregateId(), row.occurredAt(),
                    row.status().name(), row.attempts(), row.lastError(), row.correlationId(), row.causationId(), row.traceId(),
                    row.actorId() == null ? null : actors.get(row.actorId()),
                    handlers.getOrDefault(row.id(), List.of()).stream()
                            .map(mark -> new EventTrailEntry.HandlerRun(mark.handler(), mark.processedAt())).toList(),
                    withPayload ? redacted(row) : null));
        }
        return List.copyOf(entries);
    }

    private JsonNode redacted(OutboxMessage row) {
        try {
            JsonNode tree = json.readTree(row.payload());
            if (!(tree instanceof ObjectNode payload)) {
                return tree;
            }
            Set<String> hidden = types.byType(row.type()).map(EventTypeRegistry.Registration::sensitiveComponents)
                    .orElseGet(() -> Set.copyOf(fieldNames(payload)));
            return sensitive.redact(payload, hidden);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static List<String> fieldNames(ObjectNode payload) {
        List<String> names = new ArrayList<>();
        payload.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
