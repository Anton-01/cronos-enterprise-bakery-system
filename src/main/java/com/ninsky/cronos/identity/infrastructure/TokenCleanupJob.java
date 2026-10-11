package com.ninsky.cronos.identity.infrastructure;

import com.ninsky.cronos.eventing.infrastructure.ClusterJobRunner;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;

/**
 * Hourly: deletes single-use links spent or expired more than a week ago, finished two-factor challenges and expired
 * stream tickets. Nothing here is needed for any answer once expired; the week keeps recent links explainable to support.
 */
@Component
public class TokenCleanupJob {

    static final Duration LINK_RETENTION = Duration.ofDays(7);

    private final ClusterJobRunner jobs;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public TokenCleanupJob(ClusterJobRunner jobs, NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jobs = jobs;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.auth.token-cleanup-cron:0 7 * * * *}")
    public void run() {
        jobs.run("TokenCleanupJob", () -> {
            Timestamp now = Timestamp.from(clock.instant());
            Timestamp cutoff = Timestamp.from(clock.instant().minus(LINK_RETENTION));
            int links = jdbc.update("DELETE FROM user_tokens WHERE expires_at < :cutoff OR used_at < :cutoff", Map.of("cutoff", cutoff));
            int challenges = jdbc.update("DELETE FROM two_factor_challenges WHERE expires_at < :now", Map.of("now", now));
            int tickets = jdbc.update("DELETE FROM sse_tickets WHERE expires_at < :now", Map.of("now", now));
            return links + challenges + tickets;
        });
    }
}
