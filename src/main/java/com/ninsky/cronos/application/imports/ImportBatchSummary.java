package com.ninsky.cronos.application.imports;

import com.ninsky.cronos.domain.model.imports.ImportBatch;
import com.ninsky.cronos.domain.model.imports.ImportResource;
import com.ninsky.cronos.domain.model.imports.ImportStatus;

import java.time.Instant;
import java.util.UUID;

/** One line of the import history ({@code GET /data-imports}); the full report is fetched by id. */
public record ImportBatchSummary(
        UUID batchId,
        ImportResource resource,
        ImportStatus status,
        boolean dryRun,
        String fileName,
        long fileSizeBytes,
        String fileSha256,
        int totalRows,
        int created,
        int updated,
        int unchanged,
        int rejectedRows,
        int errorCount,
        int warningCount,
        String actorUsername,
        String traceId,
        Instant startedAt,
        Instant finishedAt
) {
    static ImportBatchSummary of(ImportBatch batch) {
        return new ImportBatchSummary(batch.id(), batch.resource(), batch.status(), batch.dryRun(), batch.fileName(),
                batch.fileSizeBytes(), batch.fileSha256(), batch.totalRows(), batch.createdCount(), batch.updatedCount(),
                batch.unchangedCount(), batch.rejectedRows(), batch.errorCount(), batch.warningCount(),
                batch.actorUsername(), batch.traceId(), batch.startedAt(), batch.finishedAt());
    }
}
