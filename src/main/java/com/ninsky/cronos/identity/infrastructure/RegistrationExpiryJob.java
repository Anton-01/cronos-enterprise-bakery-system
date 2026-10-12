package com.ninsky.cronos.identity.infrastructure;

import com.ninsky.cronos.eventing.infrastructure.ClusterJobRunner;
import com.ninsky.cronos.identity.application.RegistrationReviewService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Daily 03:00 (tenant time): undecided registrations past {@code expires_at} expire and their accounts are deactivated. */
@Component
public class RegistrationExpiryJob {

    static final int BATCH = 500;

    private final ClusterJobRunner jobs;
    private final RegistrationReviewService reviews;

    public RegistrationExpiryJob(ClusterJobRunner jobs, RegistrationReviewService reviews) {
        this.jobs = jobs;
        this.reviews = reviews;
    }

    @Scheduled(cron = "${app.auth.registration-expiry-cron:0 0 3 * * *}", zone = "America/Mexico_City")
    public void run() {
        jobs.run("RegistrationExpiryJob", () -> reviews.expireOverdue(BATCH));
    }
}
