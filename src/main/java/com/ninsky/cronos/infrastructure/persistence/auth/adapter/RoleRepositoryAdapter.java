package com.ninsky.cronos.infrastructure.persistence.auth.adapter;

import com.ninsky.cronos.domain.model.auth.Role;
import com.ninsky.cronos.domain.port.auth.RoleRepositoryPort;
import com.ninsky.cronos.infrastructure.persistence.auth.PermissionJpaRepository;
import com.ninsky.cronos.infrastructure.persistence.auth.RoleJpaRepository;
import com.ninsky.cronos.infrastructure.persistence.auth.entity.PermissionJpaEntity;
import com.ninsky.cronos.infrastructure.persistence.auth.entity.RoleJpaEntity;
import com.ninsky.cronos.infrastructure.persistence.auth.mapper.RoleMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Component
public class RoleRepositoryAdapter implements RoleRepositoryPort {

    private final RoleJpaRepository jpaRepository;
    private final PermissionJpaRepository permissionJpaRepository;
    private final RoleMapper mapper;

    public RoleRepositoryAdapter(RoleJpaRepository jpaRepository, PermissionJpaRepository permissionJpaRepository, RoleMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.permissionJpaRepository = permissionJpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Role save(Role role) {
        Set<PermissionJpaEntity> permissions = role.getPermissionIds().isEmpty()
                ? Set.of()
                : StreamSupport.stream(permissionJpaRepository.findAllById(role.getPermissionIds()).spliterator(), false).collect(Collectors.toSet());
        RoleJpaEntity saved = jpaRepository.save(mapper.toEntity(role, permissions));
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Role> findById(Long id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<Role> findAllById(Iterable<Long> ids) {
        return jpaRepository.findAllById(ids).stream().map(mapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<Role> findAll() {
        return jpaRepository.findAll().stream().map(mapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public Set<Role> findByNameIn(Set<String> roleNames) {
        return jpaRepository.findByNameIn(roleNames).stream().map(mapper::toDomain).collect(Collectors.toSet());
    }

    @Override
    public Optional<Role> findByName(String name) {
        return jpaRepository.findByName(name).map(mapper::toDomain);
    }

    @Override
    public boolean existsByNameIgnoreCase(String name) {
        return jpaRepository.existsByNameIgnoreCase(name);
    }
}
