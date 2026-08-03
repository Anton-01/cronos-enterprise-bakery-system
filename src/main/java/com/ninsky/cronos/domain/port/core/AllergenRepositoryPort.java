package com.ninsky.cronos.domain.port.core;

import com.ninsky.cronos.domain.entity.enums.RecordStatus;
import com.ninsky.cronos.domain.model.core.Allergen;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AllergenRepositoryPort {

    Allergen save(Allergen allergen);

    Optional<Allergen> findById(UUID id);

    List<Allergen> findAllById(Iterable<UUID> ids);

    Optional<Allergen> findByName(String name);

    boolean existsByName(String name);

    Page<Allergen> findAllByOrderByIdAsc(Pageable pageable);

    Page<Allergen> findSystemAllergens(Pageable pageable);

    int updateStatus(UUID id, RecordStatus status);
}
