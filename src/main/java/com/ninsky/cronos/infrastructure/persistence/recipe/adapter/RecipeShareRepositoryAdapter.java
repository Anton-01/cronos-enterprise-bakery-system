package com.ninsky.cronos.infrastructure.persistence.recipe.adapter;

import com.ninsky.cronos.domain.model.recipe.RecipeShare;
import com.ninsky.cronos.domain.port.recipe.RecipeShareRepositoryPort;
import com.ninsky.cronos.infrastructure.persistence.recipe.RecipeShareJpaRepository;
import com.ninsky.cronos.infrastructure.persistence.recipe.mapper.RecipeShareMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class RecipeShareRepositoryAdapter implements RecipeShareRepositoryPort {

    private final RecipeShareJpaRepository jpaRepository;
    private final RecipeShareMapper mapper;

    public RecipeShareRepositoryAdapter(RecipeShareJpaRepository jpaRepository, RecipeShareMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public RecipeShare save(RecipeShare share) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(share)));
    }

    @Override
    public Optional<RecipeShare> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<RecipeShare> findByShareToken(String shareToken) {
        return jpaRepository.findByShareToken(shareToken).map(mapper::toDomain);
    }

    @Override
    public List<RecipeShare> findByRecipeIdOrderByCreatedAtDesc(UUID recipeId) {
        return jpaRepository.findByRecipeIdOrderByCreatedAtDesc(recipeId).stream().map(mapper::toDomain).collect(Collectors.toList());
    }
}
