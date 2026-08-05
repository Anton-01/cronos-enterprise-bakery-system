package com.ninsky.cronos.domain.port.auth;

import com.ninsky.cronos.domain.model.auth.Permission;

import java.util.List;
import java.util.Optional;

public interface PermissionRepositoryPort {

    Permission save(Permission permission);

    Optional<Permission> findById(Long id);

    List<Permission> findAllById(Iterable<Long> ids);

    List<Permission> findAll();
}
