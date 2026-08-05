package com.ninsky.cronos.domain.port.auth;

import com.ninsky.cronos.domain.model.auth.UserSession;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserSessionRepositoryPort {

    UserSession save(UserSession session);

    Optional<UserSession> findById(UUID id);

    void terminateAllUserSessions(UUID userId, LocalDateTime now, String reason);

    List<UserSession> findByUserIdAndIsActiveTrueOrderByLastActivityAtDesc(UUID userId);

    List<UserSession> findByUserIdAndIsActiveTrueOrderByCreatedAtAsc(UUID userId);
}
