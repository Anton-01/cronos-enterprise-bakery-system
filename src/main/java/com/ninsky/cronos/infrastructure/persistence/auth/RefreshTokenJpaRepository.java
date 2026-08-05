package com.ninsky.cronos.infrastructure.persistence.auth;

import com.ninsky.cronos.infrastructure.persistence.auth.entity.RefreshTokenJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenJpaEntity, UUID> {

    Optional<RefreshTokenJpaEntity> findByToken(String token);

    @Modifying
    @Query("UPDATE RefreshTokenJpaEntity r SET r.revoked = true, r.revokedAt = :now WHERE r.userId = :userId AND r.revoked = false")
    void revokeAllUserTokens(@Param("userId") UUID userId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE RefreshTokenJpaEntity r SET r.revoked = true, r.revokedAt = CURRENT_TIMESTAMP WHERE r.sessionId = :sessionId")
    void revokeTokensBySessionId(@Param("sessionId") UUID sessionId);
}
