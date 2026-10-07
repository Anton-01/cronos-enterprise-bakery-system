package com.ninsky.cronos.iam.signin;

import com.ninsky.cronos.iam.shared.TenantTime;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Stores a new password (existing encoder) and its history entry, clears the forced-change flag and
 * the failed-attempt counter, bumps {@code version}. JDBC like every other IAM user write.
 */
@Component
@RequiredArgsConstructor
public class CredentialWriter {

    private final CredentialCustomRepository repository;
    private final PasswordEncoder encoder;
    private final AuthProjectionCache authCache;
    private final Clock clock;

    @Transactional
    public void setPassword(UUID userId, String rawPassword, boolean emailVerified) {
        String hash = encoder.encode(rawPassword);
        repository.storePassword(userId, hash, TenantTime.nowLocal(clock), emailVerified);
        authCache.evictAfterCommit();
    }

    /** First sign-in with a temporary password ends in a successful change: PENDING_ACTIVATION → ACTIVE. */
    @Transactional
    public boolean activateIfPending(UUID userId) {
        boolean activated = repository.activateIfPending(userId);
        authCache.evictAfterCommit();
        return activated;
    }
}
