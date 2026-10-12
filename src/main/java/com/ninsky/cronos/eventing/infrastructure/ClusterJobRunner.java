package com.ninsky.cronos.eventing.infrastructure;

import com.ninsky.cronos.eventing.application.TraceContexts;
import com.ninsky.cronos.infrastructure.persistence.lock.AdvisoryLockCustomRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.function.IntSupplier;
import java.util.zip.CRC32;

/**
 * Runs a scheduled job on one node at a time, the way the existing jobs do it: a transaction-scoped PostgreSQL
 * advisory lock keyed by the job name (no ShedLock table needed). Each run gets a fresh trace context, so the events
 * it publishes share one correlation id, and reports {@code cronos.job.duration{job}} / {@code cronos.job.items{job}}.
 */
@Slf4j
@Component
public class ClusterJobRunner {

    private static final long NAMESPACE = 0x4352_4F4EL << 32;

    private final AdvisoryLockCustomRepository locks;
    private final TransactionTemplate outer;
    private final MeterRegistry meters;
    private final Clock clock;

    public ClusterJobRunner(AdvisoryLockCustomRepository locks, PlatformTransactionManager transactions, MeterRegistry meters, Clock clock) {
        this.locks = locks;
        this.outer = new TransactionTemplate(transactions);
        this.meters = meters;
        this.clock = clock;
    }

    /**
     * Runs {@code work} unless another node is running {@code job}. The work runs inside the lock-holding
     * transaction; items it wants to commit independently must use {@code REQUIRES_NEW}.
     *
     * @return the item count reported by {@code work}, or -1 when the run was skipped
     */
    public int run(String job, IntSupplier work) {
        Timer.Sample sample = Timer.start(meters);
        try {
            Integer items = outer.execute(status -> {
                if (!locks.tryLockForTransaction(lockKey(job))) {
                    return -1;
                }
                return TraceContexts.callWith(TraceContexts.newRoot(clock), work::getAsInt);
            });
            int result = items == null ? 0 : items;
            if (result >= 0) {
                meters.summary("cronos.job.items", "job", job).record(result);
                if (result > 0) {
                    log.info("Job {} processed {} item(s)", job, result);
                }
            }
            return result;
        } catch (RuntimeException e) {
            log.error("Job {} failed: {}", job, e.getMessage(), e);
            return 0;
        } finally {
            sample.stop(meters.timer("cronos.job.duration", "job", job));
        }
    }

    /** Stable advisory-lock key of {@code job}, in a namespace that does not collide with the hand-picked keys. */
    static long lockKey(String job) {
        CRC32 crc = new CRC32();
        crc.update(job.getBytes(StandardCharsets.UTF_8));
        return NAMESPACE | crc.getValue();
    }
}
