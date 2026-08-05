package com.ninsky.cronos.infrastructure.persistence.auth.adapter;

import com.ninsky.cronos.domain.model.auth.Permission;
import com.ninsky.cronos.domain.port.auth.PermissionRepositoryPort;
import com.ninsky.cronos.infrastructure.persistence.auth.PermissionJpaRepository;
import com.ninsky.cronos.infrastructure.persistence.auth.entity.PermissionJpaEntity;
import com.ninsky.cronos.infrastructure.persistence.auth.mapper.PermissionMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class PermissionRepositoryAdapter implements PermissionRepositoryPort {

    private final PermissionJpaRepository jpaRepository;
    private final PermissionMapper mapper;

    public PermissionRepositoryAdapter(PermissionJpaRepository jpaRepository, PermissionMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Permission save(Permission permission) {
        PermissionJpaEntity saved = jpaRepository.save(mapper.toEntity(permission));
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Permission> findById(Long id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<Permission> findAllById(Iterable<Long> ids) {
        return jpaRepository.findAllById(ids).stream().map(mapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<Permission> findAll() {
        return jpaRepository.findAll().stream().map(mapper::toDomain).collect(Collectors.toList());
    }
}
