package com.ninsky.cronos.infrastructure.persistence.auth;

import com.ninsky.cronos.infrastructure.persistence.auth.entity.RoleJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;

@Repository
public interface RoleJpaRepository extends JpaRepository<RoleJpaEntity, Long> {

    /** Matches the role code too: system roles now carry display names ("Super administrador"). */
    @Query(value = "SELECT * FROM roles WHERE name IN (:names) OR code IN (:names)", nativeQuery = true)
    Set<RoleJpaEntity> findByNameIn(@Param("names") Set<String> roleNames);

    @Query(value = "SELECT * FROM roles WHERE code = :name OR name = :name ORDER BY (code = :name) DESC LIMIT 1", nativeQuery = true)
    Optional<RoleJpaEntity> findByName(@Param("name") String name);

    boolean existsByNameIgnoreCase(String name);
}
