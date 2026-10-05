package com.ninsky.cronos.finance.shared;

import java.math.BigDecimal;
import java.time.temporal.TemporalAccessor;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Builds the audit {@code changes} map: only fields whose value changed, as {@code {field: {from, to}}}. */
public final class Changes {

    private final Map<String, Object> changed = new LinkedHashMap<>();

    public static Changes start() {
        return new Changes();
    }

    public Changes track(String field, Object from, Object to) {
        if (!same(from, to)) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("from", plain(from));
            entry.put("to", plain(to));
            changed.put(field, entry);
        }
        return this;
    }

    public boolean isEmpty() {
        return changed.isEmpty();
    }

    public Map<String, Object> build() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(changed));
    }

    private static boolean same(Object a, Object b) {
        if (a instanceof BigDecimal x && b instanceof BigDecimal y) {
            return x.compareTo(y) == 0;
        }
        return Objects.equals(a, b);
    }

    private static Object plain(Object value) {
        if (value instanceof Enum<?> e) {
            return e.name();
        }
        return value instanceof TemporalAccessor ? value.toString() : value;
    }
}
