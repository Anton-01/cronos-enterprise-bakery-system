package com.ninsky.cronos.domain.port.recipe;

import com.ninsky.cronos.domain.model.recipe.UserFixedCost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface UserFixedCostRepositoryPort {
    UserFixedCost save(UserFixedCost cost);
    Optional<UserFixedCost> findByIdAndUserId(UUID id, UUID userId);
    /** Active and inactive rows (the client filters, baking-studio §4.2). */
    Page<UserFixedCost> findByUserId(UUID userId, Pageable pageable);
    Page<UserFixedCost> findByUserIdAndNameContainingIgnoreCase(UUID userId, String name, Pageable pageable);
    void delete(UUID id);
}
