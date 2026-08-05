package com.ninsky.cronos.infrastructure.persistence.recipe.adapter;

import com.ninsky.cronos.domain.model.recipe.RecipeShareAccessLog;
import com.ninsky.cronos.domain.port.recipe.RecipeShareAccessLogRepositoryPort;
import com.ninsky.cronos.infrastructure.persistence.recipe.RecipeShareAccessLogJpaRepository;
import com.ninsky.cronos.infrastructure.persistence.recipe.mapper.RecipeShareAccessLogMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class RecipeShareAccessLogRepositoryAdapter implements RecipeShareAccessLogRepositoryPort {

    private final RecipeShareAccessLogJpaRepository jpaRepository;
    private final RecipeShareAccessLogMapper mapper;

    public RecipeShareAccessLogRepositoryAdapter(RecipeShareAccessLogJpaRepository jpaRepository, RecipeShareAccessLogMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public RecipeShareAccessLog save(RecipeShareAccessLog log) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(log)));
    }

    @Override
    public List<RecipeShareAccessLog> findByRecipeShareIdOrderByAccessedAtDesc(UUID recipeShareId) {
        return jpaRepository.findByRecipeShareIdOrderByAccessedAtDesc(recipeShareId).stream().map(mapper::toDomain).collect(Collectors.toList());
    }
}
