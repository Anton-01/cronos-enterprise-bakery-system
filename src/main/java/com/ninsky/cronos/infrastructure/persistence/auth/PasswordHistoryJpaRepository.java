package com.ninsky.cronos.infrastructure.persistence.auth;

import com.ninsky.cronos.infrastructure.persistence.auth.entity.PasswordHistoryJpaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PasswordHistoryJpaRepository extends JpaRepository<PasswordHistoryJpaEntity, UUID> {
    List<PasswordHistoryJpaEntity> findByUserIdOrderByChangedAtDesc(UUID userId, Pageable pageable);
}
