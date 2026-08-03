package com.ninsky.cronos.infrastructure.persistence.core;

import com.ninsky.cronos.domain.entity.enums.RecordStatus;
import com.ninsky.cronos.infrastructure.persistence.core.entity.CategoryJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CategoryJpaRepository extends JpaRepository<CategoryJpaEntity, Long> {
    Optional<CategoryJpaEntity> findByName(String name);
    boolean existsByName(String name);

    @Query("SELECT c FROM CategoryJpaEntity c WHERE c.isSystemDefault = true order by c.id asc")
    Page<CategoryJpaEntity> findSystemCategories(Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE CategoryJpaEntity c SET c.status = :status, c.updatedAt = CURRENT_TIMESTAMP WHERE c.id = :id")
    int updateStatus(@Param("id") Long id, @Param("status") RecordStatus status);
}
