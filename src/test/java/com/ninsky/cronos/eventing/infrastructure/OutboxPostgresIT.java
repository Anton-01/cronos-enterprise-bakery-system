package com.ninsky.cronos.eventing.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ninsky.cronos.eventing.application.DomainEventPublisher;
import com.ninsky.cronos.eventing.application.EventEnvelope;
import com.ninsky.cronos.eventing.application.OutboxHandler;
import com.ninsky.cronos.eventing.application.PublishOptions;
import com.ninsky.cronos.eventing.application.TraceContext;
import com.ninsky.cronos.eventing.application.TraceContexts;
import com.ninsky.cronos.iam.shared.ActorProvider;
import com.ninsky.cronos.infrastructure.security.crypto.FieldEncryptionService;
import eventfixtures.OrderFixtures.OrderPlaced;
import eventfixtures.OrderFixtures.OrderShipped;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * §8 against a real PostgreSQL: publication only inside a transaction, dedupe, at-most-once handler effects under
 * redelivery, retries with back-off, two concurrent relays without double dispatch, per-aggregate ordering,
 * causation chains and sensitive components encrypted at rest.
 */
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=true"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({OutboxCustomRepository.class, OutboxDomainEventPublisher.class, SensitivePayloadCodec.class, OutboxSignal.class,
        OutboxDispatcher.class, OutboxRelay.class, EventTrailCustomRepository.class, OutboxPostgresIT.Beans.class})
class OutboxPostgresIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static final Map<UUID, AtomicInteger> PLACED_CALLS = new ConcurrentHashMap<>();
    static final List<Integer> SEQUENCE = new CopyOnWriteArrayList<>();
    static final Set<UUID> FAIL_ONCE = ConcurrentHashMap.newKeySet();
    static final Map<UUID, EventEnvelope> SHIPPED = new ConcurrentHashMap<>();

    @MockitoBean
    private ActorProvider actors;
    @MockitoBean
    private FieldEncryptionService encryption;

    @Autowired
    private DomainEventPublisher publisher;
    @Autowired
    private OutboxRelay relay;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactions;

    @TestConfiguration
    static class Beans {
        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        EventTypeRegistry eventTypeRegistry() {
            return EventTypeRegistry.of(List.of(OrderPlaced.class, OrderShipped.class));
        }

        @Bean
        EventingProperties eventingProperties() {
            return new EventingProperties(false, Duration.ofMillis(500), 100, 8, Duration.ofMinutes(5), Duration.ofDays(30));
        }

        @Bean
        OutboxHandler<OrderPlaced> placedHandler(DomainEventPublisher publisher) {
            return new OutboxHandler<>() {
                @Override
                public Class<OrderPlaced> eventClass() {
                    return OrderPlaced.class;
                }

                @Override
                public void handle(OrderPlaced event, EventEnvelope envelope) {
                    if (FAIL_ONCE.remove(event.orderId())) {
                        throw new IllegalStateException("transient failure");
                    }
                    PLACED_CALLS.computeIfAbsent(event.orderId(), id -> new AtomicInteger()).incrementAndGet();
                    SEQUENCE.add(event.sequence());
                    if ("ship".equals(event.secret())) {
                        publisher.publish(new OrderShipped(event.orderId()));
                    }
                }

                @Override
                public String name() {
                    return "PlacedHandler";
                }
            };
        }

        @Bean
        OutboxHandler<OrderShipped> shippedHandler() {
            return new OutboxHandler<>() {
                @Override
                public Class<OrderShipped> eventClass() {
                    return OrderShipped.class;
                }

                @Override
                public void handle(OrderShipped event, EventEnvelope envelope) {
                    SHIPPED.put(event.orderId(), envelope);
                }

                @Override
                public String name() {
                    return "ShippedHandler";
                }
            };
        }
    }

    @BeforeEach
    void setUp() {
        when(encryption.encrypt(any())).thenAnswer(inv -> "ENC(" + inv.getArgument(0) + ")");
        when(encryption.decrypt(any())).thenAnswer(inv -> {
            String sealed = inv.getArgument(0);
            return sealed.substring(4, sealed.length() - 1);
        });
        jdbc.update("DELETE FROM processed_events");
        jdbc.update("DELETE FROM outbox_messages");
        PLACED_CALLS.clear();
        SEQUENCE.clear();
        FAIL_ONCE.clear();
        SHIPPED.clear();
    }

    private void inTransaction(Runnable work) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> work.run());
    }

    @Test
    void publishingRequiresATransaction() {
        assertThatThrownBy(() -> publisher.publish(new OrderPlaced(UUID.randomUUID(), 1, "x")))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void sensitiveComponentsAreEncryptedAtRestAndRestoredForHandlers() {
        UUID order = UUID.randomUUID();
        inTransaction(() -> publisher.publish(new OrderPlaced(order, 1, "top-secret")));
        String stored = jdbc.queryForObject("SELECT payload ->> 'secret' FROM outbox_messages", String.class);
        assertThat(stored).isEqualTo("enc:ENC(\"top-secret\")");

        assertThat(relay.drainNow()).isEqualTo(1);
        assertThat(PLACED_CALLS.get(order)).hasValue(1);
        assertThat(jdbc.queryForObject("SELECT status FROM outbox_messages", String.class)).isEqualTo("DISPATCHED");
    }

    @Test
    void aDedupeKeyCoalescesRepeatedPublications() {
        UUID order = UUID.randomUUID();
        inTransaction(() -> {
            assertThat(publisher.publish(new OrderPlaced(order, 1, "a"), PublishOptions.dedupe("order:" + order))).isTrue();
            assertThat(publisher.publish(new OrderPlaced(order, 2, "b"), PublishOptions.dedupe("order:" + order))).isFalse();
        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_messages", Integer.class)).isEqualTo(1);
    }

    @Test
    void aFailedHandlerIsRetriedWithBackOffAndAppliesOnlyOnce() {
        UUID order = UUID.randomUUID();
        FAIL_ONCE.add(order);
        inTransaction(() -> publisher.publish(new OrderPlaced(order, 1, "a")));

        relay.drainNow();
        assertThat(PLACED_CALLS).doesNotContainKey(order);
        Map<String, Object> row = jdbc.queryForMap("SELECT status, attempts, available_at > now() AS later FROM outbox_messages");
        assertThat(row).containsEntry("status", "PENDING").containsEntry("attempts", 1).containsEntry("later", true);

        jdbc.update("UPDATE outbox_messages SET available_at = now()");
        relay.drainNow();
        jdbc.update("UPDATE outbox_messages SET status = 'PENDING', available_at = now()");
        relay.drainNow();
        assertThat(PLACED_CALLS.get(order)).hasValue(1);
    }

    @Test
    void concurrentRelaysNeverDispatchTwice() {
        int messages = 300;
        inTransaction(() -> {
            for (int i = 0; i < messages; i++) {
                publisher.publish(new OrderPlaced(UUID.randomUUID(), i, "a"));
            }
        });
        CompletableFuture<Integer> first = CompletableFuture.supplyAsync(relay::drainNow);
        CompletableFuture<Integer> second = CompletableFuture.supplyAsync(relay::drainNow);
        int claimed = first.join() + second.join();

        assertThat(claimed).isEqualTo(messages);
        assertThat(PLACED_CALLS).hasSize(messages);
        assertThat(PLACED_CALLS.values()).allMatch(calls -> calls.get() == 1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_messages WHERE status = 'DISPATCHED'", Integer.class))
                .isEqualTo(messages);
    }

    @Test
    void eventsOfOneAggregateAreDispatchedInOrder() {
        UUID order = UUID.randomUUID();
        inTransaction(() -> {
            for (int i = 1; i <= 5; i++) {
                publisher.publish(new OrderPlaced(order, i, "a"));
            }
        });
        while (relay.drainNow() > 0) {
            // one message of the aggregate per round
        }
        assertThat(SEQUENCE).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void effectsKeepTheCorrelationAndPointToTheirCause() {
        UUID order = UUID.randomUUID();
        TraceContext request = TraceContexts.newRoot(Clock.systemUTC());
        TraceContexts.runWith(request, () -> inTransaction(() -> publisher.publish(new OrderPlaced(order, 1, "ship"))));
        UUID placedId = jdbc.queryForObject("SELECT id FROM outbox_messages WHERE type = 'fixture.order-placed'", UUID.class);

        relay.drainNow();
        relay.drainNow();

        EventEnvelope shipped = SHIPPED.get(order);
        assertThat(shipped).isNotNull();
        assertThat(shipped.correlationId()).isEqualTo(request.correlationId());
        assertThat(shipped.causationId()).isEqualTo(placedId);
        assertThat(shipped.traceId()).isEqualTo(request.traceId());
        assertThat(new EventTrailCustomRepository(new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc))
                .causeChain(shipped.id())).extracting(OutboxMessage::id).containsExactly(placedId, shipped.id());
    }
}
