package com.ninsky.cronos.infrastructure.persistence.auth;

import com.ninsky.cronos.infrastructure.persistence.auth.entity.UserProfileJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserProfileJpaRepository extends JpaRepository<UserProfileJpaEntity, UUID> {
    Optional<UserProfileJpaEntity> findByUserId(UUID userId);
}
