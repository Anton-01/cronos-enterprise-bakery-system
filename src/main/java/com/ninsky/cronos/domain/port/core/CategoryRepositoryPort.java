package com.ninsky.cronos.domain.port.core;

import com.ninsky.cronos.domain.entity.enums.CategoryType;
import com.ninsky.cronos.domain.model.core.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface CategoryRepositoryPort {

    Category save(Category category);

    Optional<Category> findById(Long id);

    /**
     * True if a category with this name already exists in the same visibility scope: among
     * SYSTEM categories of {@code type} if {@code ownerId} is null, or among {@code ownerId}'s own
     * USER categories of {@code type} otherwise. Two different users (or a user and SYSTEM) may
     * reuse the same name — uniqueness is per-owner, not global.
     */
    boolean existsByNameForOwner(String name, CategoryType type, UUID ownerId);

    /** Same scoping rules as {@link #existsByNameForOwner}. */
    Optional<Category> findByNameForOwner(String name, CategoryType type, UUID ownerId);

    /** SYSTEM categories ∪ {@code userId}'s own USER categories (business rule 1), optionally filtered by type. */
    Page<Category> findVisibleToUser(UUID userId, CategoryType type, Pageable pageable);

    /** SYSTEM categories only, optionally filtered by type. */
    Page<Category> findSystemCategories(CategoryType type, Pageable pageable);

    /** Soft-deletes (JPA {@code @SQLDelete} intercepts this into an UPDATE, not a physical DELETE). */
    void delete(Category category);
}
