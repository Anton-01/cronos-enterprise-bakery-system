package com.ninsky.cronos.iam.sod;

import java.util.List;

/** A rule the subject's effective set violates, with the held codes that triggered it. */
public record SodConflict(String code, String name, String description, SodSeverity severity, List<String> permissions) {
    public SodConflict {
        permissions = List.copyOf(permissions);
    }

    public boolean blocking() {
        return severity == SodSeverity.BLOCKING;
    }
}
