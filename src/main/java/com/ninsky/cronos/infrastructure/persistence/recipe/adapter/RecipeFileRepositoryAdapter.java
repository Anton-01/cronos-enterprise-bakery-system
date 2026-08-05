package com.ninsky.cronos.infrastructure.persistence.recipe.adapter;

import com.ninsky.cronos.domain.model.recipe.RecipeFile;
import com.ninsky.cronos.domain.port.recipe.RecipeFileRepositoryPort;
import com.ninsky.cronos.infrastructure.persistence.recipe.RecipeFileJpaRepository;
import com.ninsky.cronos.infrastructure.persistence.recipe.mapper.RecipeFileMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class RecipeFileRepositoryAdapter implements RecipeFileRepositoryPort {

    private final RecipeFileJpaRepository jpaRepository;
    private final RecipeFileMapper mapper;

    public RecipeFileRepositoryAdapter(RecipeFileJpaRepository jpaRepository, RecipeFileMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public RecipeFile save(RecipeFile file) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(file)));
    }

    @Override
    public List<RecipeFile> findByRecipeIdOrderByCreatedAtDesc(UUID recipeId) {
        return jpaRepository.findByRecipeIdOrderByCreatedAtDesc(recipeId).stream().map(mapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public Optional<RecipeFile> findByIdAndRecipeId(UUID id, UUID recipeId) {
        return jpaRepository.findByIdAndRecipeId(id, recipeId).map(mapper::toDomain);
    }

    @Override
    public void delete(RecipeFile file) {
        jpaRepository.deleteById(file.getId());
    }

    @Override
    public void flush() {
        jpaRepository.flush();
    }
}
