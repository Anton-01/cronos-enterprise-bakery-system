package com.ninsky.cronos.eventing.infrastructure;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

/** Daily 05:00 (tenant time): deletes DISPATCHED outbox messages past the retention; DEAD ones stay for review. */
@Component
public class OutboxCleanupJob {

    private final ClusterJobRunner jobs;
    private final OutboxCustomRepository outbox;
    private final EventingProperties properties;
    private final Clock clock;

    public OutboxCleanupJob(ClusterJobRunner jobs, OutboxCustomRepository outbox, EventingProperties properties, Clock clock) {
        this.jobs = jobs;
        this.outbox = outbox;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.eventing.cleanup-cron:0 0 5 * * *}", zone = "America/Mexico_City")
    public void run() {
        jobs.run("OutboxCleanupJob", () -> outbox.purgeDispatchedBefore(clock.instant().minus(properties.dispatchedRetention())));
    }
}
