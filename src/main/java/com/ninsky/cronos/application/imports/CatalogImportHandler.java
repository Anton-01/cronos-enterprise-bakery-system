package com.ninsky.cronos.application.imports;

import com.ninsky.cronos.domain.model.audit.Actor;
import com.ninsky.cronos.domain.model.imports.ImportResource;

import java.util.List;
import java.util.UUID;

/**
 * Resource-specific half of a bulk import; {@link CatalogImportService} owns everything generic
 * (file guards, hashing, transactions, locking, ledger, audit). Both methods run inside the
 * transaction the service opens.
 *
 * @param <T> domain type written by this handler
 */
public interface CatalogImportHandler<T> {

    ImportResource resource();

    List<ColumnSpec> columns();

    /**
     * Parses every row and validates it against the current catalog — field formats, in-file
     * duplicates and every {@link com.ninsky.cronos.application.service.catalog.UnitCatalogPolicy}
     * rule. Read-only. Problems go to {@code issues}; only clean rows are returned.
     */
    List<PlannedRow<T>> plan(List<RowReader> rows, IssueCollector issues);

    /** Writes the CREATE/UPDATE rows of a plan that produced no errors; returns one result per planned row. */
    List<ImportRowResult> apply(List<PlannedRow<T>> plan, Actor actor, UUID batchId);
}
