package com.ninsky.cronos.infrastructure.persistence.imports;

import com.ninsky.cronos.domain.model.imports.ImportBatch;
import com.ninsky.cronos.domain.model.imports.ImportResource;
import com.ninsky.cronos.domain.model.imports.ImportStatus;
import com.ninsky.cronos.domain.port.imports.ImportBatchRepositoryPort;
import com.ninsky.cronos.infrastructure.persistence.imports.entity.DataImportBatchJpaEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DataImportBatchRepositoryAdapter implements ImportBatchRepositoryPort {

    /** History is always newest first; client-supplied sort is ignored rather than trusted. */
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "startedAt");

    private final DataImportBatchJpaRepository jpaRepository;

    @Override
    public ImportBatch append(ImportBatch batch) {
        return toDomain(jpaRepository.save(toEntity(batch)));
    }

    @Override
    public Optional<ImportBatch> findById(UUID id) {
        return jpaRepository.findById(id).map(DataImportBatchRepositoryAdapter::toDomain);
    }

    @Override
    public Page<ImportBatch> search(ImportResource resource, ImportStatus status, Pageable pageable) {
        Pageable newestFirst = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), NEWEST_FIRST);
        return jpaRepository.search(resource, status, newestFirst).map(DataImportBatchRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<ImportBatch> findLatestCommitted(ImportResource resource, String fileSha256) {
        return jpaRepository.findFirstByResourceAndFileSha256AndStatusOrderByFinishedAtDesc(resource, fileSha256, ImportStatus.COMMITTED)
                .map(DataImportBatchRepositoryAdapter::toDomain);
    }

    private static DataImportBatchJpaEntity toEntity(ImportBatch batch) {
        return DataImportBatchJpaEntity.builder()
                .id(batch.id())
                .resource(batch.resource())
                .status(batch.status())
                .dryRun(batch.dryRun())
                .fileName(batch.fileName())
                .fileSizeBytes(batch.fileSizeBytes())
                .fileSha256(batch.fileSha256())
                .totalRows(batch.totalRows())
                .createdCount(batch.createdCount())
                .updatedCount(batch.updatedCount())
                .unchangedCount(batch.unchangedCount())
                .rejectedRows(batch.rejectedRows())
                .errorCount(batch.errorCount())
                .warningCount(batch.warningCount())
                .actorUserId(batch.actorUserId())
                .actorUsername(batch.actorUsername())
                .traceId(batch.traceId())
                .startedAt(LocalDateTime.ofInstant(batch.startedAt(), ZoneOffset.UTC))
                .finishedAt(LocalDateTime.ofInstant(batch.finishedAt(), ZoneOffset.UTC))
                .report(batch.reportJson())
                .build();
    }

    private static ImportBatch toDomain(DataImportBatchJpaEntity entity) {
        return new ImportBatch(entity.getId(), entity.getResource(), entity.getStatus(), entity.isDryRun(),
                entity.getFileName(), entity.getFileSizeBytes(), entity.getFileSha256(),
                entity.getTotalRows(), entity.getCreatedCount(), entity.getUpdatedCount(), entity.getUnchangedCount(),
                entity.getRejectedRows(), entity.getErrorCount(), entity.getWarningCount(),
                entity.getActorUserId(), entity.getActorUsername(), entity.getTraceId(),
                utc(entity.getStartedAt()), utc(entity.getFinishedAt()), entity.getReport());
    }

    private static Instant utc(LocalDateTime value) {
        return value.toInstant(ZoneOffset.UTC);
    }
}
