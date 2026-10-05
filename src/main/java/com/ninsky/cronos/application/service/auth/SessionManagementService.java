package com.ninsky.cronos.application.service.auth;

import com.ninsky.cronos.domain.model.auth.UserSession;
import com.ninsky.cronos.domain.port.auth.RefreshTokenRepositoryPort;
import com.ninsky.cronos.domain.port.auth.UserSessionRepositoryPort;
import com.ninsky.cronos.iam.access.SessionRevoker;
import com.ninsky.cronos.iam.policy.SecurityPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Session limits of the security policy (spec §8): idle, absolute lifetime and concurrency. */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionManagementService {

    public static final String CONCURRENCY_LIMIT = "CONCURRENT_SESSION_LIMIT";
    public static final String IDLE_TIMEOUT = "IDLE_TIMEOUT";
    public static final String ABSOLUTE_TIMEOUT = "ABSOLUTE_TIMEOUT";

    private final UserSessionRepositoryPort userSessionRepository;
    private final RefreshTokenRepositoryPort refreshTokenRepository;
    private final SessionRevoker sessionRevoker;

    /** Keeps the newest {@code maxConcurrentSessions} active sessions and revokes the oldest ones. */
    @Transactional
    public int enforceConcurrencyLimit(UUID userId, int maxConcurrentSessions, LocalDateTime now) {
        List<UserSession> active = userSessionRepository.findByUserIdAndIsActiveTrueOrderByCreatedAtAsc(userId);
        List<UserSession> excess = active.stream()
                .sorted(Comparator.comparing(UserSession::getCreatedAt))
                .limit(Math.max(0, active.size() - maxConcurrentSessions))
                .toList();
        excess.forEach(session -> terminate(session, CONCURRENCY_LIMIT, now));
        if (!excess.isEmpty()) {
            log.info("Revoked {} oldest session(s) of user {} over the limit of {}", excess.size(), userId, maxConcurrentSessions);
        }
        return excess.size();
    }

    /** Why the session can no longer be used, if it hit the idle or absolute limit. */
    public Optional<String> expiry(UserSession session, SecurityPolicy policy, LocalDateTime now) {
        LocalDateTime lastActivity = Optional.ofNullable(session.getLastActivityAt()).orElse(session.getCreatedAt());
        if (session.getCreatedAt().plusHours(policy.sessionAbsoluteHours()).isBefore(now)
                || session.getExpiresAt() != null && session.getExpiresAt().isBefore(now)) {
            return Optional.of(ABSOLUTE_TIMEOUT);
        }
        return lastActivity.plusMinutes(policy.sessionIdleMinutes()).isBefore(now) ? Optional.of(IDLE_TIMEOUT) : Optional.empty();
    }

    /** Ends one session: refresh tokens revoked, live access tokens blacklisted after commit. */
    @Transactional
    public void terminate(UserSession session, String reason, LocalDateTime now) {
        session.setActive(false);
        session.setTerminatedAt(now);
        session.setTerminationReason(reason);
        userSessionRepository.save(session);
        refreshTokenRepository.revokeTokensBySessionId(session.getId());
        sessionRevoker.blacklistSession(session.getId());
    }
}
