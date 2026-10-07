package com.ninsky.cronos.iam.policy;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ninsky.cronos.iam.access.AccessChanged;
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

    private final TwoFactorRequirementCustomRepository repository;
    private final SecurityPolicyProvider policies;
    private final Cache<UUID, TwoFactorRequirementCustomRepository.State> states = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(30)).maximumSize(10_000).build();

    public JdbcTwoFactorRequirement(TwoFactorRequirementCustomRepository repository, SecurityPolicyProvider policies) {
        this.repository = repository;
        this.policies = policies;
    }

    @Override
    public boolean isRequired(UUID userId) {
        return states.get(userId, this::load).required();
    }

    @Override
    public boolean mustEnrol(UUID userId) {
        TwoFactorRequirementCustomRepository.State state = states.get(userId, this::load);
        return state.required() && !state.enabled();
    }

    @Override
    public List<String> requiredBy(UUID userId) {
        return repository.requiringRoleNames(userId, requiredRoleIds());
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

    private TwoFactorRequirementCustomRepository.State load(UUID userId) {
        return repository.state(userId, requiredRoleIds()).orElse(new TwoFactorRequirementCustomRepository.State(false, false));
    }

    private List<Long> requiredRoleIds() {
        return policies.current().twoFactorRequiredRoleIds();
    }
}
