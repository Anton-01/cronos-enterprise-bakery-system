package com.ninsky.cronos.domain.port.recipe;

import com.ninsky.cronos.domain.model.recipe.UserFixedCost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface UserFixedCostRepositoryPort {
    UserFixedCost save(UserFixedCost cost);
    Optional<UserFixedCost> findByIdAndUserId(UUID id, UUID userId);
    Page<UserFixedCost> findByUserIdAndIsActiveTrue(UUID userId, Pageable pageable);
    Page<UserFixedCost> findByUserIdAndIsActiveTrueAndNameContainingIgnoreCase(UUID userId, String name, Pageable pageable);
}
