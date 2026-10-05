package com.ninsky.cronos.iam.user;

import com.ninsky.cronos.iam.shared.TenantTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Time-driven transitions (spec §3.3): expired SUSPENDED/LOCKED back to ACTIVE, reached
 * {@code accessExpiresAt} to DEACTIVATED. One node at a time via a transaction-scoped advisory lock;
 * each user commits on its own, with a system (null) actor.
 */
@Slf4j
@Component
public class UserLifecycleJobs {

    private static final long LOCK_KEY = 7426101L;

    private final NamedParameterJdbcTemplate jdbc;
    private final UserStatusService statuses;
    private final TransactionTemplate outer;
    private final TransactionTemplate perUser;
    private final Clock clock;

    public UserLifecycleJobs(NamedParameterJdbcTemplate jdbc, UserStatusService statuses,
                             PlatformTransactionManager transactions, Clock clock) {
        this.jdbc = jdbc;
        this.statuses = statuses;
        this.outer = new TransactionTemplate(transactions);
        this.perUser = new TransactionTemplate(transactions);
        this.perUser.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.iam.lifecycle-delay-ms:60000}", initialDelayString = "${app.iam.lifecycle-initial-delay-ms:30000}")
    public void run() {
        outer.executeWithoutResult(status -> {
            if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(:key)",
                    Map.of("key", LOCK_KEY), Boolean.class))) {
                return;
            }
            revertExpired();
            expireAccess();
        });
    }

    void revertExpired() {
        List<UUID> ids = jdbc.queryForList("""
                        SELECT id FROM users WHERE status IN ('SUSPENDED', 'LOCKED') AND status_until IS NOT NULL
                        AND status_until <= :now""",
                new MapSqlParameterSource("now", Timestamp.from(TenantTime.now(clock))), UUID.class);
        ids.forEach(id -> transition(id, new UserStatusWriter.Change(UserStatus.ACTIVE, null, "AUTO_REVERT", null, null)));
    }

    void expireAccess() {
        List<UUID> ids = jdbc.queryForList("""
                        SELECT id FROM users WHERE status <> 'DEACTIVATED' AND access_expires_at IS NOT NULL
                        AND access_expires_at <= :today""",
                new MapSqlParameterSource("today", TenantTime.today(clock)), UUID.class);
        ids.forEach(id -> transition(id,
                new UserStatusWriter.Change(UserStatus.DEACTIVATED, StatusReason.OFFBOARDING, "ACCESS_EXPIRED", null, null)));
    }

    private void transition(UUID id, UserStatusWriter.Change change) {
        try {
            perUser.executeWithoutResult(s -> statuses.apply(null, id, change, null));
        } catch (RuntimeException e) {
            log.warn("Lifecycle transition to {} skipped for user {}: {}", change.status(), id, e.getMessage());
        }
    }
}
