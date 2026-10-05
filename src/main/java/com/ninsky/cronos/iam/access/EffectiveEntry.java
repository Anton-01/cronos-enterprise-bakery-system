package com.ninsky.cronos.iam.access;

import java.util.List;

/** One code of the access view: granted or not, explicitly denied or not, and why. */
public record EffectiveEntry(String code, boolean granted, boolean deniedExplicitly, List<PermissionSource> sources) {
    public EffectiveEntry {
        sources = List.copyOf(sources);
    }
}
