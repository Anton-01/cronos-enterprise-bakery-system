package com.ninsky.cronos.application.imports;

/** What a valid row does (or, on a dry run, would do) to the catalog. */
public enum ImportAction {
    CREATE,
    UPDATE,
    UNCHANGED
}
