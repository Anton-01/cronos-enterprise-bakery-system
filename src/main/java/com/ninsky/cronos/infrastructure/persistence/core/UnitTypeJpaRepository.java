package com.ninsky.cronos.infrastructure.persistence.core;

import com.ninsky.cronos.domain.entity.enums.RecordStatus;
import com.ninsky.cronos.infrastructure.persistence.core.entity.UnitTypeJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UnitTypeJpaRepository extends JpaRepository<UnitTypeJpaEntity, Long> {
    Optional<UnitTypeJpaEntity> findByName(String name);
    boolean existsByName(String name);
    boolean existsByCodeIdentityEqualsIgnoreCase(String codeIdentity);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE UnitTypeJpaEntity e SET e.status = :status, e.updatedAt = CURRENT_TIMESTAMP WHERE e.id = :id")
    int updateStatus(@Param("id") Long id, @Param("status") RecordStatus status);

    // SUPER ADMIN METHODS
    // 1. Obtener la "Papelera de Reciclaje" (Solo los borrados)
    @Query(value = "SELECT * FROM unit_types WHERE deleted_at IS NOT NULL", nativeQuery = true)
    Page<UnitTypeJpaEntity> findAllTrash(Pageable pageable);

    // 2. Restaurar un registro
    @Modifying // Necesario cuando haces UPDATE/DELETE manual
    @Query(value = "UPDATE unit_types SET deleted_at = NULL WHERE id = :id", nativeQuery = true)
    void restoreById(Long id);
}
