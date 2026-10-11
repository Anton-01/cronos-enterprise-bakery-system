package com.ninsky.cronos.quote.infrastructure;

import com.ninsky.cronos.eventing.infrastructure.ClusterJobRunner;
import com.ninsky.cronos.quote.application.QuoteLifecycleService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Validity jobs of quotes (§4.7), each under a cluster-wide advisory lock: every 15 minutes open quotes past
 * {@code valid_until} expire; daily at 09:00 (tenant time) {@code quote.expiring} is announced once per quote.
 */
@Component
public class QuoteExpiryJobs {

    static final int BATCH = 500;

    private final ClusterJobRunner jobs;
    private final QuoteLifecycleService lifecycle;

    public QuoteExpiryJobs(ClusterJobRunner jobs, QuoteLifecycleService lifecycle) {
        this.jobs = jobs;
        this.lifecycle = lifecycle;
    }

    @Scheduled(cron = "${app.quotes.expiry-cron:0 */15 * * * *}", zone = "America/Mexico_City")
    public void expireOverdue() {
        jobs.run("QuoteExpiryJob", () -> lifecycle.expireOverdue(BATCH));
    }

    @Scheduled(cron = "${app.quotes.expiring-cron:0 0 9 * * *}", zone = "America/Mexico_City")
    public void announceExpiring() {
        jobs.run("QuoteExpiringJob", lifecycle::announceExpiring);
    }
}
