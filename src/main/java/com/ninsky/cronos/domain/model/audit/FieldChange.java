package com.ninsky.cronos.domain.model.audit;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.SequencedMap;
import java.util.Set;

/**
 * One changed field, serialized into {@code audit_log.changes} as {@code {"field": {"from": .., "to": ..}}}.
 * {@code null} means "absent / cleared". Catalog data carries no PII, so values are stored as-is.
 */
public record FieldChange(Object from, Object to) {

    /** Field-level diff of two snapshots, in snapshot key order; unchanged keys are dropped. */
    public static SequencedMap<String, FieldChange> diff(Map<String, ?> before, Map<String, ?> after) {
        Set<String> keys = new LinkedHashSet<>(before.keySet());
        keys.addAll(after.keySet());
        SequencedMap<String, FieldChange> changes = new LinkedHashMap<>();
        for (String key : keys) {
            Object from = before.get(key);
            Object to = after.get(key);
            if (!Objects.equals(from, to)) {
                changes.put(key, new FieldChange(from, to));
            }
        }
        return Collections.unmodifiableSequencedMap(changes);
    }

    /** Every field of a newly created (or deleted) record, as a diff from/to nothing. */
    public static SequencedMap<String, FieldChange> created(Map<String, ?> after) {
        return diff(Map.of(), after);
    }
}
