package com.ninsky.cronos.infrastructure.persistence.recipe.adapter;

import com.ninsky.cronos.domain.model.recipe.UserFixedCost;
import com.ninsky.cronos.domain.port.recipe.UserFixedCostRepositoryPort;
import com.ninsky.cronos.infrastructure.persistence.recipe.UserFixedCostJpaRepository;
import com.ninsky.cronos.infrastructure.persistence.recipe.mapper.UserFixedCostMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class UserFixedCostRepositoryAdapter implements UserFixedCostRepositoryPort {

    private final UserFixedCostJpaRepository jpaRepository;
    private final UserFixedCostMapper mapper;

    public UserFixedCostRepositoryAdapter(UserFixedCostJpaRepository jpaRepository, UserFixedCostMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public UserFixedCost save(UserFixedCost cost) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(cost)));
    }

    @Override
    public Optional<UserFixedCost> findByIdAndUserId(UUID id, UUID userId) {
        return jpaRepository.findByIdAndUserId(id, userId).map(mapper::toDomain);
    }

    @Override
    public Page<UserFixedCost> findByUserId(UUID userId, Pageable pageable) {
        return jpaRepository.findByUserId(userId, pageable).map(mapper::toDomain);
    }

    @Override
    public Page<UserFixedCost> findByUserIdAndNameContainingIgnoreCase(UUID userId, String name, Pageable pageable) {
        return jpaRepository.findByUserIdAndNameContainingIgnoreCase(userId, name, pageable).map(mapper::toDomain);
    }

    @Override
    public void delete(UUID id) {
        jpaRepository.deleteById(id);
    }
}
