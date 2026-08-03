package com.ninsky.cronos.infrastructure.persistence.core;

import com.ninsky.cronos.domain.entity.core.Allergen;
import com.ninsky.cronos.domain.entity.enums.RecordStatus;
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
public interface AllergenRepository extends JpaRepository<Allergen, UUID> {
    Optional<Allergen> findByName(String name);
    boolean existsByName(String name);

    @Query("SELECT a FROM Allergen a WHERE a.isSystemDefault = true")
    Page<Allergen> findSystemAllergens(Pageable pageable);

    Page<Allergen> findAllByOrderByIdAsc(Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Allergen a SET a.status = :status, a.updatedAt = CURRENT_TIMESTAMP WHERE a.id = :id")
    int updateStatus(@Param("id") UUID id, @Param("status") RecordStatus status);
}
