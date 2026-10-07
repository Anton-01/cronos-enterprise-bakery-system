package com.ninsky.cronos.iam.user;

import com.ninsky.cronos.iam.access.AccessVersions;
import com.ninsky.cronos.iam.access.SessionRevoker;
import com.ninsky.cronos.iam.shared.TenantTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The one place that moves a user between statuses: status columns, the legacy
 * enabled/account_non_locked/locked_until flags, version, access version and session revocation
 * (always when leaving ACTIVE, spec §1.4.5).
 * Callers validate the transition and write the audit event.
 */
@Component
@RequiredArgsConstructor
public class UserStatusWriter {

    private final UserStatusCustomRepository repository;
    private final AccessVersions accessVersions;
    private final SessionRevoker sessionRevoker;
    private final Clock clock;

    public record Change(UserStatus status, StatusReason reason, String comment, Instant until, UUID changedBy) {
    }

    /** @return false when the row was not at {@code expectedVersion} (null skips the check) */
    @Transactional
    public boolean apply(UUID userId, Change change, Long expectedVersion) {
        if (!repository.apply(userId, change, TenantTime.now(clock), expectedVersion)) {
            return false;
        }
        accessVersions.bump(List.of(userId));
        if (change.status() != UserStatus.ACTIVE && change.status() != UserStatus.PENDING_ACTIVATION) {
            sessionRevoker.revokeAll(userId, "STATUS_" + change.status().name());
        }
        return true;
    }
}
