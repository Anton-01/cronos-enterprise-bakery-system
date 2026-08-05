package com.ninsky.cronos.infrastructure.persistence.recipe.adapter;

import com.ninsky.cronos.domain.model.recipe.IngredientSubstitute;
import com.ninsky.cronos.domain.port.recipe.IngredientSubstituteRepositoryPort;
import com.ninsky.cronos.infrastructure.persistence.recipe.IngredientSubstituteJpaRepository;
import com.ninsky.cronos.infrastructure.persistence.recipe.mapper.IngredientSubstituteMapper;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class IngredientSubstituteRepositoryAdapter implements IngredientSubstituteRepositoryPort {

    private final IngredientSubstituteJpaRepository jpaRepository;
    private final IngredientSubstituteMapper mapper;

    public IngredientSubstituteRepositoryAdapter(IngredientSubstituteJpaRepository jpaRepository, IngredientSubstituteMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public IngredientSubstitute save(IngredientSubstitute substitute) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(substitute)));
    }

    @Override
    public Optional<IngredientSubstitute> findByUserIdAndOriginalIngredientIdAndSubstituteMaterialId(UUID userId, UUID originalIngredientId, UUID substituteMaterialId) {
        return jpaRepository.findByUserIdAndOriginalIngredientIdAndSubstituteMaterialId(userId, originalIngredientId, substituteMaterialId).map(mapper::toDomain);
    }
}
