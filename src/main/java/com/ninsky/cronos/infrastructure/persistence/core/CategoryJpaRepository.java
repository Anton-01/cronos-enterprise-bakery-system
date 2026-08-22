package com.ninsky.cronos.infrastructure.persistence.core;

import com.ninsky.cronos.domain.entity.enums.CategoryType;
import com.ninsky.cronos.infrastructure.persistence.core.entity.CategoryJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryJpaRepository extends JpaRepository<CategoryJpaEntity, Long>, JpaSpecificationExecutor<CategoryJpaEntity> {

    /**
     * {@code ownerId} null means "among SYSTEM rows"; non-null means "among that owner's USER
     * rows". Written as an explicit query rather than a derived {@code existsByNameAndTypeAndUserId}
     * because Spring Data's derived-query equality (`= ?1`) never matches a null parameter against
     * `user_id IS NULL` — SQL's `NULL = NULL` is unknown, not true.
     */
    @Query("""
            SELECT COUNT(c) > 0 FROM CategoryJpaEntity c
            WHERE lower(c.name) = lower(:name) AND c.type = :type
              AND ((:ownerId IS NULL AND c.userId IS NULL) OR c.userId = :ownerId)
            """)
    boolean existsByNameForOwner(@Param("name") String name, @Param("type") CategoryType type, @Param("ownerId") UUID ownerId);

    @Query("""
            SELECT c FROM CategoryJpaEntity c
            WHERE lower(c.name) = lower(:name) AND c.type = :type
              AND ((:ownerId IS NULL AND c.userId IS NULL) OR c.userId = :ownerId)
            """)
    Optional<CategoryJpaEntity> findByNameForOwner(@Param("name") String name, @Param("type") CategoryType type, @Param("ownerId") UUID ownerId);
}
