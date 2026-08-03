package com.ninsky.cronos.infrastructure.persistence.core;

import com.ninsky.cronos.domain.entity.enums.RecordStatus;
import com.ninsky.cronos.infrastructure.persistence.core.entity.AllergenJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AllergenJpaRepository extends JpaRepository<AllergenJpaEntity, UUID> {
    Optional<AllergenJpaEntity> findByName(String name);
    boolean existsByName(String name);

    @Query("SELECT a FROM AllergenJpaEntity a WHERE a.isSystemDefault = true")
    Page<AllergenJpaEntity> findSystemAllergens(Pageable pageable);

    Page<AllergenJpaEntity> findAllByOrderByIdAsc(Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE AllergenJpaEntity a SET a.status = :status, a.updatedAt = CURRENT_TIMESTAMP WHERE a.id = :id")
    int updateStatus(@Param("id") UUID id, @Param("status") RecordStatus status);
}
