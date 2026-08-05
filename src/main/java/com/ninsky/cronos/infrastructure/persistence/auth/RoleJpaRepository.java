package com.ninsky.cronos.infrastructure.persistence.auth;

import com.ninsky.cronos.infrastructure.persistence.auth.entity.RoleJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;

@Repository
public interface RoleJpaRepository extends JpaRepository<RoleJpaEntity, Long> {
    Set<RoleJpaEntity> findByNameIn(Set<String> roleNames);
    Optional<RoleJpaEntity> findByName(String name);
    boolean existsByNameIgnoreCase(String name);
}
