package com.ninsky.cronos.notification.infrastructure;

import com.ninsky.cronos.eventing.infrastructure.ClusterJobRunner;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;

/**
 * Daily 04:00 (tenant time): notifications past their retention (read after 90 days, all after 180) and push delivery
 * records older than 30 days are deleted (§6.1, §8.7).
 */
@Component
public class NotificationRetentionJob {

    static final Duration DELIVERY_RETENTION = Duration.ofDays(30);

    private final ClusterJobRunner jobs;
    private final NotificationCustomRepository notifications;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public NotificationRetentionJob(ClusterJobRunner jobs, NotificationCustomRepository notifications, NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jobs = jobs;
        this.notifications = notifications;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.notifications.retention-cron:0 0 4 * * *}", zone = "America/Mexico_City")
    public void run() {
        jobs.run("NotificationRetentionJob", () -> notifications.purge(clock.instant())
                + jdbc.update("DELETE FROM push_deliveries WHERE created_at < :before",
                new MapSqlParameterSource("before", Timestamp.from(clock.instant().minus(DELIVERY_RETENTION)))));
    }
}
