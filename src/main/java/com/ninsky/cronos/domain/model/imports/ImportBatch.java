package com.ninsky.cronos.domain.model.imports;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of the append-only import ledger ({@code data_import_batches}). The summary columns are
 * queryable; {@code reportJson} holds the full row-level report exactly as returned to the user.
 */
public record ImportBatch(
        UUID id,
        ImportResource resource,
        ImportStatus status,
        boolean dryRun,
        String fileName,
        long fileSizeBytes,
        String fileSha256,
        int totalRows,
        int createdCount,
        int updatedCount,
        int unchangedCount,
        int rejectedRows,
        int errorCount,
        int warningCount,
        UUID actorUserId,
        String actorUsername,
        String traceId,
        Instant startedAt,
        Instant finishedAt,
        String reportJson
) {
}
