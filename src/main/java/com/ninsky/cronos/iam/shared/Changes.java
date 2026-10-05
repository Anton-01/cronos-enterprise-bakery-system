package com.ninsky.cronos.iam.shared;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Builds the audit {@code changes} map: only changed fields, as {@code {from, to}}. */
public final class Changes {

    private Changes() {
    }

    public static void put(Map<String, Object> changes, String field, Object from, Object to) {
        if (!Objects.equals(from, to)) {
            Map<String, Object> change = new LinkedHashMap<>();
            change.put("from", from);
            change.put("to", to);
            changes.put(field, change);
        }
    }

    public static Map<String, Object> of(String field, Object from, Object to) {
        Map<String, Object> changes = new LinkedHashMap<>();
        put(changes, field, from, to);
        return changes;
    }
}
