package com.ninsky.cronos.infrastructure.persistence.core.adapter;

import com.ninsky.cronos.domain.entity.enums.RecordStatus;
import com.ninsky.cronos.domain.model.core.UnitType;
import com.ninsky.cronos.domain.port.core.UnitTypeRepositoryPort;
import com.ninsky.cronos.infrastructure.persistence.core.UnitTypeJpaRepository;
import com.ninsky.cronos.infrastructure.persistence.core.entity.UnitTypeJpaEntity;
import com.ninsky.cronos.infrastructure.persistence.core.mapper.UnitTypeMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class UnitTypeRepositoryAdapter implements UnitTypeRepositoryPort {

    private final UnitTypeJpaRepository jpaRepository;
    private final UnitTypeMapper mapper;

    public UnitTypeRepositoryAdapter(UnitTypeJpaRepository jpaRepository, UnitTypeMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public UnitType save(UnitType unitType) {
        UnitTypeJpaEntity saved = jpaRepository.save(mapper.toEntity(unitType));
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<UnitType> findById(Long id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<UnitType> findByName(String name) {
        return jpaRepository.findByName(name).map(mapper::toDomain);
    }

    @Override
    public boolean existsByName(String name) {
        return jpaRepository.existsByName(name);
    }

    @Override
    public boolean existsByCodeIdentityEqualsIgnoreCase(String codeIdentity) {
        return jpaRepository.existsByCodeIdentityEqualsIgnoreCase(codeIdentity);
    }

    @Override
    public Page<UnitType> findAll(Pageable pageable) {
        return jpaRepository.findAll(pageable).map(mapper::toDomain);
    }

    @Override
    public int updateStatus(Long id, RecordStatus status) {
        return jpaRepository.updateStatus(id, status);
    }

    @Override
    public void delete(UnitType unitType) {
        jpaRepository.delete(mapper.toEntity(unitType));
    }
}
