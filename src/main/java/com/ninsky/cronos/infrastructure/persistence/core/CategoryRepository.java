package com.ninsky.cronos.infrastructure.persistence.core;

import com.ninsky.cronos.domain.entity.core.Category;
import com.ninsky.cronos.domain.entity.enums.RecordStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    Optional<Category> findByName(String name);
    boolean existsByName(String name);

    @Query("SELECT c FROM Category c WHERE c.isSystemDefault = true order by c.id asc")
    Page<Category> findSystemCategories(Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Category c SET c.status = :status, c.updatedAt = CURRENT_TIMESTAMP WHERE c.id = :id")
    int updateStatus(@Param("id") Long id, @Param("status") RecordStatus status);
}
