package com.ninsky.cronos.domain.port.auth;

import com.ninsky.cronos.domain.model.auth.Role;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface RoleRepositoryPort {

    Role save(Role role);

    Optional<Role> findById(Long id);

    List<Role> findAllById(Iterable<Long> ids);

    List<Role> findAll();

    Set<Role> findByNameIn(Set<String> roleNames);

    Optional<Role> findByName(String name);

    boolean existsByNameIgnoreCase(String name);
}
