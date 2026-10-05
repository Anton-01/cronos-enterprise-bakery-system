package com.ninsky.cronos.iam.access;

import com.ninsky.cronos.domain.port.auth.RefreshTokenRepositoryPort;
import com.ninsky.cronos.domain.port.auth.UserSessionRepositoryPort;
import com.ninsky.cronos.infrastructure.config.security.JwtConfig;
import com.ninsky.cronos.infrastructure.security.blacklist.TokenBlacklistService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/** Revokes refresh tokens, sessions and live access tokens (§1.4.5). */
@Component
@RequiredArgsConstructor
public class SessionRevoker {

    private final RefreshTokenRepositoryPort refreshTokens;
    private final UserSessionRepositoryPort sessions;
    private final TokenBlacklistService blacklist;
    private final JwtConfig jwtConfig;

    /** Next refresh fails, so a new claim must be minted by logging in again. */
    public void revokeRefreshTokens(UUID userId) {
        refreshTokens.revokeAllUserTokens(userId, LocalDateTime.now());
    }

    /** Everything: refresh tokens, sessions and already-issued access tokens. */
    public void revokeAll(UUID userId, String reason) {
        LocalDateTime now = LocalDateTime.now();
        refreshTokens.revokeAllUserTokens(userId, now);
        sessions.terminateAllUserSessions(userId, now, reason);
        afterCommit(() -> blacklist.blacklistUser(userId, Duration.ofMillis(jwtConfig.getAccessTokenExpiration())));
    }

    /** One session; its access tokens stop working too. */
    public void blacklistSession(UUID sessionId) {
        afterCommit(() -> blacklist.blacklistSession(sessionId, Duration.ofMillis(jwtConfig.getAccessTokenExpiration())));
    }

    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
