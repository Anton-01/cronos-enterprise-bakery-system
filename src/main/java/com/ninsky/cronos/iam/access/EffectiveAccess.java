package com.ninsky.cronos.iam.access;

import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** Result of the resolver: per-code entries in canonical catalog order. */
public record EffectiveAccess(boolean superAdmin, List<EffectiveEntry> entries) {

    public EffectiveAccess {
        entries = List.copyOf(entries);
    }

    /** The codes actually held. */
    public SortedSet<String> granted() {
        return entries.stream().filter(EffectiveEntry::granted).map(EffectiveEntry::code)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    public boolean holds(String code) {
        return granted().contains(code);
    }

    public static Set<String> added(EffectiveAccess before, EffectiveAccess after) {
        SortedSet<String> added = after.granted();
        added.removeAll(before.granted());
        return added;
    }

    public static Set<String> removed(EffectiveAccess before, EffectiveAccess after) {
        return added(after, before);
    }
}
