package com.ninsky.cronos.domain.port.auth;

import com.ninsky.cronos.domain.model.auth.RefreshToken;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepositoryPort {

    RefreshToken save(RefreshToken refreshToken);

    Optional<RefreshToken> findByToken(String token);

    void revokeAllUserTokens(UUID userId, LocalDateTime now);

    void revokeTokensBySessionId(UUID sessionId);
}
