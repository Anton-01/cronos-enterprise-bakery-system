package com.ninsky.cronos.application.imports;

import com.ninsky.cronos.domain.model.audit.FieldChange;

import java.util.Map;
import java.util.SequencedMap;
import java.util.function.Function;

/**
 * A validated row and what applying it means: {@code current} is the stored record ({@code null}
 * for a CREATE), {@code candidate} the record as it will be saved.
 */
public record PlannedRow<T>(int row, String key, ImportAction action, T current, T candidate,
                            SequencedMap<String, FieldChange> changes) {

    /** CREATE when nothing is stored, UNCHANGED when the snapshots are equal, UPDATE otherwise. */
    public static <T> PlannedRow<T> of(int row, String key, T current, T candidate, Function<T, Map<String, Object>> snapshot) {
        Map<String, Object> after = snapshot.apply(candidate);
        SequencedMap<String, FieldChange> changes = current == null
                ? FieldChange.created(after)
                : FieldChange.diff(snapshot.apply(current), after);
        ImportAction action = current == null ? ImportAction.CREATE : changes.isEmpty() ? ImportAction.UNCHANGED : ImportAction.UPDATE;
        return new PlannedRow<>(row, key, action, current, candidate, changes);
    }

    public ImportRowResult toResult(Long recordId) {
        return new ImportRowResult(row, key, action, recordId, changes);
    }
}
