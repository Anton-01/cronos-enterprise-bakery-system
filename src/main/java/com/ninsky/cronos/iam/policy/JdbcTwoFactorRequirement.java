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
 * Reads the 2FA requirement and enrolment state from the database. The gate asks on every request,
 * so answers are cached per user and evicted on enrolment, access or policy changes.
 */
@Component
public class JdbcTwoFactorRequirement implements TwoFactorRequirement {

    /** Roles that count: ACTIVE, listed in the policy, never SUPER_ADMIN (§8.3). */
    private static final String REQUIRING_ROLES = """
            FROM user_roles ur JOIN roles r ON r.id = ur.role_id
            WHERE ur.user_id = u.id AND r.status = 'ACTIVE' AND r.code <> 'SUPER_ADMIN' AND r.id = ANY (:roleIds)""";

    private record State(boolean required, boolean enabled) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final SecurityPolicyProvider policies;
    private final Cache<UUID, State> states = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(30)).maximumSize(10_000).build();

    public JdbcTwoFactorRequirement(NamedParameterJdbcTemplate jdbc, SecurityPolicyProvider policies) {
        this.jdbc = jdbc;
        this.policies = policies;
    }

    @Override
    public boolean isRequired(UUID userId) {
        return states.get(userId, this::load).required();
    }

    @Override
    public boolean mustEnrol(UUID userId) {
        State state = states.get(userId, this::load);
        return state.required() && !state.enabled();
    }

    @Override
    public List<String> requiredBy(UUID userId) {
        return jdbc.queryForList("SELECT r.name " + REQUIRING_ROLES.replace("u.id", ":id") + " ORDER BY lower(r.name)",
                parameters(userId), String.class);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(SecurityPolicyChanged event) {
        states.invalidateAll();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(AccessChanged event) {
        states.invalidateAll(event.userIds());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(TwoFactorChanged event) {
        states.invalidate(event.userId());
    }

    private State load(UUID userId) {
        List<State> rows = jdbc.query("SELECT u.require_two_factor OR EXISTS (SELECT 1 " + REQUIRING_ROLES + ") AS required, "
                        + "u.two_factor_enabled AS enabled FROM users u WHERE u.id = :id",
                parameters(userId), (rs, i) -> new State(rs.getBoolean("required"), rs.getBoolean("enabled")));
        return rows.isEmpty() ? new State(false, false) : rows.getFirst();
    }

    private MapSqlParameterSource parameters(UUID userId) {
        return new MapSqlParameterSource("id", userId)
                .addValue("roleIds", policies.current().twoFactorRequiredRoleIds().toArray(Long[]::new));
    }
}
