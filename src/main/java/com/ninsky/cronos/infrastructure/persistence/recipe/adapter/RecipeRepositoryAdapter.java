package com.ninsky.cronos.infrastructure.persistence.recipe.adapter;

import com.ninsky.cronos.domain.model.recipe.Recipe;
import com.ninsky.cronos.domain.port.recipe.RecipeRepositoryPort;
import com.ninsky.cronos.infrastructure.persistence.recipe.RecipeJpaRepository;
import com.ninsky.cronos.infrastructure.persistence.recipe.entity.RecipeJpaEntity;
import com.ninsky.cronos.infrastructure.persistence.recipe.mapper.RecipeMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class RecipeRepositoryAdapter implements RecipeRepositoryPort {

    private final RecipeJpaRepository jpaRepository;
    private final RecipeMapper mapper;

    public RecipeRepositoryAdapter(RecipeJpaRepository jpaRepository, RecipeMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Recipe save(Recipe recipe) {
        RecipeJpaEntity saved = jpaRepository.save(mapper.toEntity(recipe));
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Recipe> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Recipe> findByIdAndUserId(UUID recipeId, UUID userId) {
        return jpaRepository.findByIdAndUserId(recipeId, userId).map(mapper::toDomain);
    }

    @Override
    public Page<Recipe> findByUserIdAndNameContainingIgnoreCase(UUID userId, String search, Pageable pageable) {
        return jpaRepository.findByUserIdAndNameContainingIgnoreCase(userId, search, pageable).map(mapper::toDomain);
    }

    @Override
    public Page<Recipe> findByUserId(UUID userId, Pageable pageable) {
        return jpaRepository.findByUserId(userId, pageable).map(mapper::toDomain);
    }

    @Override
    public boolean existsByIdAndUserId(UUID recipeId, UUID userId) {
        return jpaRepository.existsByIdAndUserId(recipeId, userId);
    }

    @Override
    public List<Recipe> findTop15ByUserIdAndNameContainingIgnoreCase(UUID userId, String name) {
        return jpaRepository.findTop15ByUserIdAndNameContainingIgnoreCase(userId, name).stream().map(mapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<Recipe> findTop15ByUserIdOrderByUpdatedAtDesc(UUID userId) {
        return jpaRepository.findTop15ByUserIdOrderByUpdatedAtDesc(userId).stream().map(mapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public void markRecipesAsNeedingRecalculationByRawMaterialId(UUID rawMaterialId) {
        jpaRepository.markRecipesAsNeedingRecalculationByRawMaterialId(rawMaterialId);
    }
}
