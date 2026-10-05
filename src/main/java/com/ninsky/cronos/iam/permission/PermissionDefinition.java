package com.ninsky.cronos.iam.permission;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** One code-defined capability {@code MODULE.RESOURCE.ACTION}. */
public record PermissionDefinition(String code, String module, String resource, String action,
                                   PermissionRisk risk, Set<String> dependsOn) {

    private static final List<String> ACTION_ORDER = List.of("READ", "CREATE", "UPDATE", "DELETE");

    /** Module, resource, then READ/CREATE/UPDATE/DELETE, then the rest alphabetically. */
    public static final Comparator<PermissionDefinition> CANONICAL_ORDER = Comparator
            .comparing(PermissionDefinition::module)
            .thenComparing(PermissionDefinition::resource)
            .thenComparingInt(PermissionDefinition::actionRank)
            .thenComparing(PermissionDefinition::action);

    public PermissionDefinition {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(risk, "risk");
        dependsOn = Set.copyOf(dependsOn);
    }

    /** Parses module/resource/action from the code; non-READ actions depend on their own READ. */
    public static PermissionDefinition of(String code, PermissionRisk risk, String... extraDependencies) {
        return parse(code, risk, true, extraDependencies);
    }

    /** For resources without a READ action (e.g. FINANCE.SETTINGS.UPDATE). */
    public static PermissionDefinition standalone(String code, PermissionRisk risk, String... dependencies) {
        return parse(code, risk, false, dependencies);
    }

    private static PermissionDefinition parse(String code, PermissionRisk risk, boolean dependsOnOwnRead, String... extraDependencies) {
        String[] parts = code.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Permission code must be MODULE.RESOURCE.ACTION: " + code);
        }
        var deps = new TreeSet<>(List.of(extraDependencies));
        if (dependsOnOwnRead && !"READ".equals(parts[2])) {
            deps.add(parts[0] + '.' + parts[1] + ".READ");
        }
        return new PermissionDefinition(code, parts[0], parts[1], parts[2], risk, deps);
    }

    public boolean isRead() {
        return "READ".equals(action);
    }

    private int actionRank() {
        int index = ACTION_ORDER.indexOf(action);
        return index < 0 ? ACTION_ORDER.size() : index;
    }
}
