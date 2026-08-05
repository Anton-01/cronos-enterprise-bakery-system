package com.ninsky.cronos.application.service.auth;

import com.ninsky.cronos.domain.model.auth.User;
import com.ninsky.cronos.domain.model.auth.UserSession;
import com.ninsky.cronos.domain.port.auth.RefreshTokenRepositoryPort;
import com.ninsky.cronos.domain.port.auth.UserSessionRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionManagementService {

    private final UserSessionRepositoryPort userSessionRepository;
    private final RefreshTokenRepositoryPort refreshTokenRepository;

    @Async("systemTaskExecutor")
    @Transactional
    public void cleanupConcurrentSessionsAsync(User user, int maxConcurrentSessions) {
        try {
            long startTime = System.currentTimeMillis();
            List<UserSession> activeSessions = userSessionRepository.findByUserIdAndIsActiveTrueOrderByCreatedAtAsc(user.getId());

            if (activeSessions.size() >= maxConcurrentSessions) {
                int sessionsToKill = (activeSessions.size() - maxConcurrentSessions) + 1;

                for (int i = 0; i < sessionsToKill; i++) {
                    UserSession oldSession = activeSessions.get(i);
                    oldSession.setActive(false);
                    oldSession.setTerminatedAt(LocalDateTime.now());
                    oldSession.setTerminationReason(":: CRONOS :: Session has been killed by another session login");

                    refreshTokenRepository.revokeTokensBySessionId(oldSession.getId());
                    userSessionRepository.save(oldSession);
                }

                log.info("Async cleanup: Invalidated {} old concurrent sessions for user {} in {} ms", sessionsToKill, user.getUsername(), (System.currentTimeMillis() - startTime));
            }
        } catch (Exception e) {
            log.error("Failed to execute async session cleanup for user {}", user.getUsername(), e);
        }
    }
}
