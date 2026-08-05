package com.ninsky.cronos.infrastructure.persistence.recipe;

import com.ninsky.cronos.infrastructure.persistence.recipe.entity.UserFixedCostJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserFixedCostJpaRepository extends JpaRepository<UserFixedCostJpaEntity, UUID> {
    Optional<UserFixedCostJpaEntity> findByIdAndUserId(UUID id, UUID userId);
    Page<UserFixedCostJpaEntity> findByUserIdAndIsActiveTrue(UUID userId, Pageable pageable);
    Page<UserFixedCostJpaEntity> findByUserIdAndIsActiveTrueAndNameContainingIgnoreCase(UUID userId, String name, Pageable pageable);
}
