package com.ninsky.cronos.application.imports;

import com.ninsky.cronos.domain.model.audit.FieldChange;

import java.util.Map;

/**
 * Per-row outcome of a valid row. {@code key} is the row's natural key (the code); {@code recordId}
 * is filled once committed; {@code changes} lists the field diffs of an UPDATE (or every field of a
 * CREATE), the same shape written to {@code audit_log.changes}.
 */
public record ImportRowResult(int row, String key, ImportAction action, Long recordId, Map<String, FieldChange> changes) {

    public ImportRowResult {
        changes = changes == null ? Map.of() : changes;
    }
}
