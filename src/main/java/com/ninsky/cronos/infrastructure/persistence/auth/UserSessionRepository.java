package com.ninsky.cronos.infrastructure.persistence.auth;

import com.ninsky.cronos.domain.entity.auth.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    @Modifying
    @Query("UPDATE UserSession s SET s.isActive = false, s.terminatedAt = :now, s.terminationReason = :reason WHERE s.user.id = :userId AND s.isActive = true")
    void terminateAllUserSessions(@Param("userId") UUID userId, @Param("now") LocalDateTime now, @Param("reason") String reason);

    List<UserSession> findByUserIdAndIsActiveTrueOrderByLastActivityAtDesc(UUID userId);

    List<UserSession> findByUserIdAndIsActiveTrueOrderByCreatedAtAsc(UUID id);
}