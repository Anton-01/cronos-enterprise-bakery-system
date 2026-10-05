package com.ninsky.cronos.iam.policy;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ninsky.cronos.iam.access.AccessChanged;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * 2FA is mandatory for users flagged {@code require_two_factor} or holding an ACTIVE role listed in
 * the policy. Answers are cached for 30 s per user (the gate asks on every request).
 */
@Component
public class JdbcTwoFactorRequirement implements TwoFactorRequirement {

    private final NamedParameterJdbcTemplate jdbc;
    private final SecurityPolicyProvider policies;
    private final Cache<UUID, Boolean> answers = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(30)).maximumSize(10_000).build();

    public JdbcTwoFactorRequirement(NamedParameterJdbcTemplate jdbc, SecurityPolicyProvider policies) {
        this.jdbc = jdbc;
        this.policies = policies;
    }

    @Override
    public boolean isRequired(UUID userId) {
        return answers.get(userId, this::load);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(SecurityPolicyChanged event) {
        answers.invalidateAll();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(AccessChanged event) {
        answers.invalidateAll(event.userIds());
    }

    private boolean load(UUID userId) {
        List<Long> roleIds = policies.current().twoFactorRequiredRoleIds();
        List<Boolean> rows = jdbc.queryForList("""
                SELECT u.require_two_factor OR EXISTS (
                           SELECT 1 FROM user_roles ur JOIN roles r ON r.id = ur.role_id
                           WHERE ur.user_id = u.id AND r.status = 'ACTIVE' AND r.id = ANY (:roleIds))
                FROM users u WHERE u.id = :id""",
                new MapSqlParameterSource("id", userId).addValue("roleIds", roleIds.toArray(Long[]::new)), Boolean.class);
        return !rows.isEmpty() && Boolean.TRUE.equals(rows.getFirst());
    }
}
