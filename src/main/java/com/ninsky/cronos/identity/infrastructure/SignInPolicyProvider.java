package com.ninsky.cronos.identity.infrastructure;

import com.ninsky.cronos.iam.policy.SecurityPolicyChanged;
import com.ninsky.cronos.identity.domain.SignInPolicy;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;
import java.util.Optional;

/** Sign-in settings of the singleton {@code security_policy} row, cached briefly and dropped when the policy changes. */
@Component
public class SignInPolicyProvider {

    static final String CACHE = "signInPolicy";

    private final NamedParameterJdbcTemplate jdbc;
    private final CacheManager caches;

    public SignInPolicyProvider(NamedParameterJdbcTemplate jdbc, CacheManager caches) {
        this.jdbc = jdbc;
        this.caches = caches;
    }

    /** The current settings, or {@link SignInPolicy#DEFAULTS} when the policy row does not exist yet. */
    @Cacheable(value = CACHE, key = "'current'")
    public SignInPolicy current() {
        return jdbc.query("""
                        SELECT max_failed_attempts, lockout_minutes, lockout_backoff_multiplier, lockout_max_minutes,
                               failure_window_minutes, screen_lock_minutes, remember_me_days, require_email_verification,
                               registration_ttl_days, disallow_user_info
                        FROM security_policy WHERE id = 1""", Map.of(),
                        (rs, row) -> new SignInPolicy(rs.getInt("max_failed_attempts"), rs.getInt("lockout_minutes"),
                                rs.getInt("lockout_backoff_multiplier"), rs.getInt("lockout_max_minutes"),
                                rs.getInt("failure_window_minutes"), rs.getInt("screen_lock_minutes"), rs.getInt("remember_me_days"),
                                rs.getBoolean("require_email_verification"), rs.getInt("registration_ttl_days"),
                                rs.getBoolean("disallow_user_info"), SignInPolicy.DEFAULTS.lockMemoryHours()))
                .stream().findFirst().orElse(SignInPolicy.DEFAULTS);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(SecurityPolicyChanged event) {
        Optional.ofNullable(caches.getCache(CACHE)).ifPresent(cache -> cache.evict("current"));
    }
}
