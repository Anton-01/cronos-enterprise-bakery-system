package com.ninsky.cronos.domain.port.imports;

import com.ninsky.cronos.domain.model.imports.ImportBatch;
import com.ninsky.cronos.domain.model.imports.ImportResource;
import com.ninsky.cronos.domain.model.imports.ImportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

/** Append-only: there is deliberately no update or delete (the table rejects both). */
public interface ImportBatchRepositoryPort {

    ImportBatch append(ImportBatch batch);

    Optional<ImportBatch> findById(UUID id);

    /** {@code resource} / {@code status} are optional filters. */
    Page<ImportBatch> search(ImportResource resource, ImportStatus status, Pageable pageable);

    /** The latest COMMITTED batch of the same file content, for re-upload detection. */
    Optional<ImportBatch> findLatestCommitted(ImportResource resource, String fileSha256);
}
