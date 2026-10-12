package com.ninsky.cronos.eventing.infrastructure;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Moves committed outbox messages to their handlers. Woken right after every commit that published something
 * ({@link OutboxSignal}) and polled every {@code app.eventing.poll-interval} as a safety net (retries, other
 * instances' messages). Safe to run on every instance: claiming is {@code FOR UPDATE SKIP LOCKED} with a lease, so
 * no ShedLock is needed. Messages of one batch belong to different aggregates and are dispatched in parallel.
 */
@Slf4j
@Component
public class OutboxRelay {

    private static final int MAX_ROUNDS_PER_DRAIN = 50;

    private final OutboxCustomRepository outbox;
    private final OutboxDispatcher dispatcher;
    private final EventingProperties properties;
    private final TransactionTemplate claimTransaction;
    private final Clock clock;
    private final ExecutorService loop = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("outbox-relay").daemon().factory());
    private final ExecutorService workers;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean pending = new AtomicBoolean();
    private volatile double lagSeconds;
    private volatile boolean stopped;

    public OutboxRelay(OutboxCustomRepository outbox, OutboxDispatcher dispatcher, OutboxSignal signal, EventingProperties properties,
                       PlatformTransactionManager transactions, MeterRegistry meters, Clock clock) {
        this.outbox = outbox;
        this.dispatcher = dispatcher;
        this.properties = properties;
        this.claimTransaction = new TransactionTemplate(transactions);
        this.clock = clock;
        this.workers = Executors.newFixedThreadPool(properties.concurrency(), Thread.ofVirtual().name("outbox-dispatch-", 0).factory());
        Gauge.builder("cronos.outbox.lag.seconds", this, relay -> relay.lagSeconds).register(meters);
        if (properties.relayEnabled()) {
            signal.subscribe(this::trigger);
        }
    }

    /** Fallback poll; also refreshes the lag gauge. */
    @Scheduled(fixedDelayString = "${app.eventing.poll-interval:500ms}", initialDelayString = "${app.eventing.initial-delay:5s}")
    public void poll() {
        if (!properties.relayEnabled() || stopped) {
            return;
        }
        try {
            lagSeconds = outbox.lagSeconds(clock.instant());
        } catch (RuntimeException e) {
            log.debug("Could not read the outbox lag: {}", e.getMessage());
        }
        trigger();
    }

    /** Requests a drain; coalesces with one already running. Returns immediately. */
    public void trigger() {
        if (stopped) {
            return;
        }
        pending.set(true);
        if (running.compareAndSet(false, true)) {
            loop.execute(this::runLoop);
        }
    }

    /**
     * Drains due messages on the calling thread until none is left (tests and the admin "retry now").
     *
     * @return messages dispatched
     */
    public int drainNow() {
        int total = 0;
        for (int round = 0; round < MAX_ROUNDS_PER_DRAIN; round++) {
            List<OutboxMessage> batch = claimTransaction.execute(status ->
                    outbox.claim(clock.instant(), properties.lease(), properties.batchSize()));
            if (batch == null || batch.isEmpty()) {
                break;
            }
            CompletableFuture.allOf(batch.stream()
                    .map(message -> CompletableFuture.runAsync(() -> dispatcher.dispatch(message), workers))
                    .toArray(CompletableFuture[]::new)).join();
            total += batch.size();
            if (batch.size() < properties.batchSize()) {
                break;
            }
        }
        return total;
    }

    private void runLoop() {
        try {
            while (pending.getAndSet(false) && !stopped) {
                drainNow();
            }
        } catch (RuntimeException e) {
            log.error("Outbox relay round failed: {}", e.getMessage(), e);
        } finally {
            running.set(false);
            if (pending.get() && !stopped && running.compareAndSet(false, true)) {
                loop.execute(this::runLoop);
            }
        }
    }

    /** Lets the in-flight round finish (bounded) and stops accepting new ones. */
    @PreDestroy
    public void stop() {
        stopped = true;
        loop.shutdown();
        workers.shutdown();
        try {
            if (!loop.awaitTermination(10, TimeUnit.SECONDS)) {
                loop.shutdownNow();
            }
            workers.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
