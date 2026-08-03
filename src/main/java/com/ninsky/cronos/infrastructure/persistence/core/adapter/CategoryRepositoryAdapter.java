package com.ninsky.cronos.infrastructure.persistence.core.adapter;

import com.ninsky.cronos.domain.entity.enums.RecordStatus;
import com.ninsky.cronos.domain.model.core.Category;
import com.ninsky.cronos.domain.port.core.CategoryRepositoryPort;
import com.ninsky.cronos.infrastructure.persistence.core.CategoryJpaRepository;
import com.ninsky.cronos.infrastructure.persistence.core.entity.CategoryJpaEntity;
import com.ninsky.cronos.infrastructure.persistence.core.mapper.CategoryMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class CategoryRepositoryAdapter implements CategoryRepositoryPort {

    private final CategoryJpaRepository jpaRepository;
    private final CategoryMapper mapper;

    public CategoryRepositoryAdapter(CategoryJpaRepository jpaRepository, CategoryMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Category save(Category category) {
        CategoryJpaEntity saved = jpaRepository.save(mapper.toEntity(category));
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Category> findById(Long id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Category> findByName(String name) {
        return jpaRepository.findByName(name).map(mapper::toDomain);
    }

    @Override
    public boolean existsByName(String name) {
        return jpaRepository.existsByName(name);
    }

    @Override
    public Page<Category> findAll(Pageable pageable) {
        return jpaRepository.findAll(pageable).map(mapper::toDomain);
    }

    @Override
    public Page<Category> findSystemCategories(Pageable pageable) {
        return jpaRepository.findSystemCategories(pageable).map(mapper::toDomain);
    }

    @Override
    public int updateStatus(Long id, RecordStatus status) {
        return jpaRepository.updateStatus(id, status);
    }
}
