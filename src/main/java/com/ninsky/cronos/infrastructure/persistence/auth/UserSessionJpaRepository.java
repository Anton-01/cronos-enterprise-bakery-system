package com.ninsky.cronos.infrastructure.persistence.auth;

import com.ninsky.cronos.infrastructure.persistence.auth.entity.UserSessionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface UserSessionJpaRepository extends JpaRepository<UserSessionJpaEntity, UUID> {

    @Modifying
    @Query("UPDATE UserSessionJpaEntity s SET s.isActive = false, s.terminatedAt = :now, s.terminationReason = :reason WHERE s.userId = :userId AND s.isActive = true")
    void terminateAllUserSessions(@Param("userId") UUID userId, @Param("now") LocalDateTime now, @Param("reason") String reason);

    List<UserSessionJpaEntity> findByUserIdAndIsActiveTrueOrderByLastActivityAtDesc(UUID userId);

    List<UserSessionJpaEntity> findByUserIdAndIsActiveTrueOrderByCreatedAtAsc(UUID userId);
}
