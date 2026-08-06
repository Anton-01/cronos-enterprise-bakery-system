package com.ninsky.cronos.infrastructure.persistence.auth;

import com.ninsky.cronos.infrastructure.persistence.auth.entity.LoginHistoryJpaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LoginHistoryJpaRepository extends JpaRepository<LoginHistoryJpaEntity, UUID> {
    List<LoginHistoryJpaEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
