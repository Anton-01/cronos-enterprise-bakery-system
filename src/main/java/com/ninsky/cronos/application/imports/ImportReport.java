package com.ninsky.cronos.application.imports;

import com.ninsky.cronos.domain.model.imports.ImportResource;
import com.ninsky.cronos.domain.model.imports.ImportStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The full, persisted result of one import attempt — returned by the import endpoint and by
 * {@code GET /data-imports/{batchId}}. Counters always reconcile:
 * {@code totalRows = created + updated + unchanged + rejectedRows} (rejected = rows with ≥1 error).
 * On a REJECTED batch, the row actions show what <em>would</em> have happened to the valid rows.
 */
public record ImportReport(
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
        boolean issuesTruncated,
        List<ImportIssue> issues,
        List<ImportRowResult> rows,
        String actorUsername,
        String traceId,
        Instant startedAt,
        Instant finishedAt,
        long durationMs
) {
    public ImportReport {
        issues = List.copyOf(issues);
        rows = List.copyOf(rows);
    }
}
