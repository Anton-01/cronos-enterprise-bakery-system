package com.ninsky.cronos.infrastructure.persistence.core.adapter;

import com.ninsky.cronos.domain.entity.enums.RecordStatus;
import com.ninsky.cronos.domain.model.core.Allergen;
import com.ninsky.cronos.domain.port.core.AllergenRepositoryPort;
import com.ninsky.cronos.infrastructure.persistence.core.AllergenJpaRepository;
import com.ninsky.cronos.infrastructure.persistence.core.entity.AllergenJpaEntity;
import com.ninsky.cronos.infrastructure.persistence.core.mapper.AllergenMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class AllergenRepositoryAdapter implements AllergenRepositoryPort {

    private final AllergenJpaRepository jpaRepository;
    private final AllergenMapper mapper;

    public AllergenRepositoryAdapter(AllergenJpaRepository jpaRepository, AllergenMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Allergen save(Allergen allergen) {
        AllergenJpaEntity saved = jpaRepository.save(mapper.toEntity(allergen));
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Allergen> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<Allergen> findAllById(Iterable<UUID> ids) {
        return jpaRepository.findAllById(ids).stream().map(mapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public Optional<Allergen> findByName(String name) {
        return jpaRepository.findByName(name).map(mapper::toDomain);
    }

    @Override
    public boolean existsByName(String name) {
        return jpaRepository.existsByName(name);
    }

    @Override
    public Page<Allergen> findAllByOrderByIdAsc(Pageable pageable) {
        return jpaRepository.findAllByOrderByIdAsc(pageable).map(mapper::toDomain);
    }

    @Override
    public Page<Allergen> findSystemAllergens(Pageable pageable) {
        return jpaRepository.findSystemAllergens(pageable).map(mapper::toDomain);
    }

    @Override
    public int updateStatus(UUID id, RecordStatus status) {
        return jpaRepository.updateStatus(id, status);
    }
}
