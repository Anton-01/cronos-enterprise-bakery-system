package com.ninsky.cronos.infrastructure.persistence.core.adapter;

import com.ninsky.cronos.domain.entity.enums.CategoryScope;
import com.ninsky.cronos.domain.entity.enums.CategoryType;
import com.ninsky.cronos.domain.model.core.Category;
import com.ninsky.cronos.domain.port.core.CategoryRepositoryPort;
import com.ninsky.cronos.infrastructure.persistence.core.CategoryJpaRepository;
import com.ninsky.cronos.infrastructure.persistence.core.entity.CategoryJpaEntity;
import com.ninsky.cronos.infrastructure.persistence.core.mapper.CategoryMapper;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
    public boolean existsByNameForOwner(String name, CategoryType type, UUID ownerId) {
        return jpaRepository.existsByNameForOwner(name, type, ownerId);
    }

    @Override
    public Optional<Category> findByNameForOwner(String name, CategoryType type, UUID ownerId) {
        return jpaRepository.findByNameForOwner(name, type, ownerId).map(mapper::toDomain);
    }

    @Override
    public Page<Category> findVisibleToUser(UUID userId, CategoryType type, Pageable pageable) {
        Specification<CategoryJpaEntity> spec = (root, query, cb) -> {
            Predicate visibility = cb.or(
                    cb.equal(root.get("scope"), CategoryScope.SYSTEM),
                    cb.and(cb.equal(root.get("scope"), CategoryScope.USER), cb.equal(root.get("userId"), userId))
            );
            return withOptionalType(root, cb, visibility, type);
        };
        return jpaRepository.findAll(spec, pageable).map(mapper::toDomain);
    }

    @Override
    public Page<Category> findSystemCategories(CategoryType type, Pageable pageable) {
        Specification<CategoryJpaEntity> spec = (root, query, cb) -> {
            Predicate systemOnly = cb.equal(root.get("scope"), CategoryScope.SYSTEM);
            return withOptionalType(root, cb, systemOnly, type);
        };
        return jpaRepository.findAll(spec, pageable).map(mapper::toDomain);
    }

    private Predicate withOptionalType(jakarta.persistence.criteria.Root<CategoryJpaEntity> root,
                                        jakarta.persistence.criteria.CriteriaBuilder cb,
                                        Predicate base, CategoryType type) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(base);
        if (type != null) {
            predicates.add(cb.equal(root.get("type"), type));
        }
        return cb.and(predicates.toArray(new Predicate[0]));
    }

    @Override
    public void delete(Category category) {
        jpaRepository.delete(mapper.toEntity(category));
    }
}
