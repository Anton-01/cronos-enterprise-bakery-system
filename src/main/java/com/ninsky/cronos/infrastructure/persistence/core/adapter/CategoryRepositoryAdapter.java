package com.ninsky.cronos.infrastructure.persistence.core.adapter;

import com.ninsky.cronos.domain.entity.enums.CategoryType;
import com.ninsky.cronos.domain.model.core.Category;
import com.ninsky.cronos.domain.port.core.CategoryRepositoryPort;
import com.ninsky.cronos.infrastructure.persistence.core.CategoryJpaRepository;
import com.ninsky.cronos.infrastructure.persistence.core.entity.CategoryJpaEntity;
import com.ninsky.cronos.infrastructure.persistence.core.mapper.CategoryMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class CategoryRepositoryAdapter implements CategoryRepositoryPort {

    private final CategoryJpaRepository jpaRepository;
    private final CategoryMapper mapper;
    private final CategoryCustomRepository reads;

    public CategoryRepositoryAdapter(CategoryJpaRepository jpaRepository, CategoryMapper mapper, CategoryCustomRepository reads) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
        this.reads = reads;
    }

    @Override
    public Category save(Category category) {
        CategoryJpaEntity saved = jpaRepository.save(mapper.toEntity(category));
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Category> findById(Long id) {
        return reads.findById(id);
    }

    @Override
    public boolean existsByNameForOwner(String name, CategoryType type, UUID ownerId) {
        return jpaRepository.existsByNameForOwner(name, type, ownerId);
    }

    @Override
    public Optional<Category> findByNameForOwner(String name, CategoryType type, UUID ownerId) {
        return jpaRepository.findByNameForOwner(name, type, ownerId).map(mapper::toDomain);
    }

    /** Reads are plain SQL (CategoryCustomRepository); writes stay on JPA. */
    @Override
    public Page<Category> findVisibleToUser(UUID userId, CategoryType type, Pageable pageable) {
        return reads.findVisibleToUser(userId, type, pageable);
    }

    @Override
    public Page<Category> findSystemCategories(CategoryType type, Pageable pageable) {
        return reads.findSystemCategories(type, pageable);
    }

    @Override
    public void delete(Category category) {
        jpaRepository.delete(mapper.toEntity(category));
    }
}
