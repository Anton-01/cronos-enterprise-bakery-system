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
    private final UserFixedCostCustomRepository reads;

    public UserFixedCostRepositoryAdapter(UserFixedCostJpaRepository jpaRepository, UserFixedCostMapper mapper,
                                          UserFixedCostCustomRepository reads) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
        this.reads = reads;
    }

    @Override
    public UserFixedCost save(UserFixedCost cost) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(cost)));
    }

    @Override
    public Optional<UserFixedCost> findByIdAndUserId(UUID id, UUID userId) {
        return reads.findByIdAndUserId(id, userId);
    }

    @Override
    public Page<UserFixedCost> findByUserId(UUID userId, Pageable pageable) {
        return reads.findByUser(userId, null, pageable);
    }

    @Override
    public Page<UserFixedCost> findByUserIdAndNameContainingIgnoreCase(UUID userId, String name, Pageable pageable) {
        return reads.findByUser(userId, name, pageable);
    }

    @Override
    public void delete(UUID id) {
        jpaRepository.deleteById(id);
    }
}
