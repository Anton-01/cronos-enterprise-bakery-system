package com.ninsky.cronos.infrastructure.persistence.recipe;

import com.ninsky.cronos.domain.entity.recipes.UserFixedCost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserFixedCostRepository extends JpaRepository<UserFixedCost, UUID> {
    Optional<UserFixedCost> findByIdAndUserId(UUID id, UUID userId);
    Page<UserFixedCost> findByUserIdAndIsActiveTrue(UUID userId, Pageable pageable);
    Page<UserFixedCost> findByUserIdAndIsActiveTrueAndNameContainingIgnoreCase(UUID userId, String name, Pageable pageable);
}
