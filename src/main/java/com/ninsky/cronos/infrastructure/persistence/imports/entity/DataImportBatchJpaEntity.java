package com.ninsky.cronos.infrastructure.persistence.imports.entity;

import com.ninsky.cronos.domain.model.imports.ImportResource;
import com.ninsky.cronos.domain.model.imports.ImportStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Append-only ledger row ({@code V8}: an UPDATE/DELETE trigger rejects changes). {@link Immutable}
 * keeps Hibernate from ever issuing an UPDATE; {@link Persistable#isNew()} is always true so
 * {@code save} inserts directly instead of merging (the id is assigned by the application).
 * Timestamps are UTC.
 */
@Entity
@Immutable
@Table(name = "data_import_batches")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataImportBatchJpaEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ImportResource resource;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ImportStatus status;

    @Column(name = "dry_run", nullable = false)
    private boolean dryRun;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @Column(name = "file_sha256", length = 64)
    private String fileSha256;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(name = "created_count", nullable = false)
    private int createdCount;

    @Column(name = "updated_count", nullable = false)
    private int updatedCount;

    @Column(name = "unchanged_count", nullable = false)
    private int unchangedCount;

    @Column(name = "rejected_rows", nullable = false)
    private int rejectedRows;

    @Column(name = "error_count", nullable = false)
    private int errorCount;

    @Column(name = "warning_count", nullable = false)
    private int warningCount;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Column(name = "actor_username", nullable = false, length = 100)
    private String actorUsername;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at", nullable = false)
    private LocalDateTime finishedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "report", nullable = false, columnDefinition = "jsonb")
    private String report;

    @Override
    public boolean isNew() {
        return true;
    }
}
