package com.ninsky.cronos.account.shared.domain.audit;

import com.ninsky.cronos.account.shared.domain.PiiMasker;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.SequencedMap;
import java.util.SequencedSet;

/** Field-level {@code {field: {from, to}}} diff of two immutable snapshots; unchanged keys are dropped. */
public final class AuditDiff {

    private AuditDiff() {
    }

    public static SequencedMap<String, FieldDiff> between(Map<String, ?> before, Map<String, ?> after, PiiMasker masker) {
        SequencedSet<String> keys = new LinkedHashSet<>(before.keySet());
        keys.addAll(after.keySet());

        SequencedMap<String, FieldDiff> diff = new LinkedHashMap<>();
        keys.stream()
                .map(key -> Map.entry(key, new FieldDiff(before.get(key), after.get(key))))
                .filter(entry -> !Objects.equals(entry.getValue().from(), entry.getValue().to()))
                .forEach(entry -> diff.put(entry.getKey(), new FieldDiff(
                        masker.mask(entry.getKey(), entry.getValue().from()),
                        masker.mask(entry.getKey(), entry.getValue().to()))));
        return Collections.unmodifiableSequencedMap(diff);
    }
}
