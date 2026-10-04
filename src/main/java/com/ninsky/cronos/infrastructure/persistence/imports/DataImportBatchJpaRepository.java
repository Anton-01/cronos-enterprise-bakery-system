package com.ninsky.cronos.infrastructure.persistence.imports;

import com.ninsky.cronos.domain.model.imports.ImportResource;
import com.ninsky.cronos.domain.model.imports.ImportStatus;
import com.ninsky.cronos.infrastructure.persistence.imports.entity.DataImportBatchJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DataImportBatchJpaRepository extends JpaRepository<DataImportBatchJpaEntity, UUID> {

    @Query("""
            SELECT b FROM DataImportBatchJpaEntity b
            WHERE (:resource IS NULL OR b.resource = :resource)
              AND (:status IS NULL OR b.status = :status)
            """)
    Page<DataImportBatchJpaEntity> search(@Param("resource") ImportResource resource, @Param("status") ImportStatus status, Pageable pageable);

    Optional<DataImportBatchJpaEntity> findFirstByResourceAndFileSha256AndStatusOrderByFinishedAtDesc(
            ImportResource resource, String fileSha256, ImportStatus status);
}
