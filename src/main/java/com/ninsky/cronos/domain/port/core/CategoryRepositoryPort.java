package com.ninsky.cronos.domain.port.core;

import com.ninsky.cronos.domain.entity.enums.RecordStatus;
import com.ninsky.cronos.domain.model.core.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface CategoryRepositoryPort {

    Category save(Category category);

    Optional<Category> findById(Long id);

    Optional<Category> findByName(String name);

    boolean existsByName(String name);

    Page<Category> findAll(Pageable pageable);

    Page<Category> findSystemCategories(Pageable pageable);

    int updateStatus(Long id, RecordStatus status);
}
