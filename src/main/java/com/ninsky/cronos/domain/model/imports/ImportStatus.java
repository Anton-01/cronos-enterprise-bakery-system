package com.ninsky.cronos.domain.model.imports;

/** Outcome of one import attempt; every outcome is recorded in {@code data_import_batches}. */
public enum ImportStatus {
    /** Dry run without errors: committing the same file would apply exactly the reported rows. */
    VALIDATED,
    /** All rows applied in one transaction. */
    COMMITTED,
    /** At least one error: nothing was written (all-or-nothing). */
    REJECTED,
    /** Unexpected technical failure while applying: rolled back, nothing was written. */
    FAILED
}
