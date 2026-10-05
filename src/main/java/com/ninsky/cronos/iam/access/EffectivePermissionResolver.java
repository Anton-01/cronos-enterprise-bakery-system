package com.ninsky.cronos.iam.access;

import com.ninsky.cronos.iam.permission.PermissionCatalog;
import com.ninsky.cronos.iam.permission.PermissionDefinition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Pure, deterministic effective-permission resolution (spec §1.4.2): union of active roles, their
 * active groups, active user groups and direct grants, minus denials, closed over dependsOn.
 * An active SUPER_ADMIN role yields the whole catalog. Codes outside the catalog are ignored.
 */
public final class EffectivePermissionResolver {

    private EffectivePermissionResolver() {
    }

    public static EffectiveAccess resolve(AccessSnapshot snapshot) {
        Map<String, List<PermissionSource>> sources = collectSources(snapshot);
        if (snapshot.holdsActiveSuperAdmin()) {
            return new EffectiveAccess(true, PermissionCatalog.all().stream()
                    .map(d -> new EffectiveEntry(d.code(), true, false, sources.getOrDefault(d.code(), List.of())))
                    .toList());
        }
        Set<String> candidates = sources.keySet().stream()
                .filter(code -> !snapshot.denials().contains(code))
                .collect(Collectors.toSet());
        Set<String> granted = PermissionCatalog.dropOrphans(candidates);

        List<EffectiveEntry> entries = PermissionCatalog.all().stream()
                .map(PermissionDefinition::code)
                .filter(code -> sources.containsKey(code) || snapshot.denials().contains(code))
                .map(code -> new EffectiveEntry(code, granted.contains(code), snapshot.denials().contains(code),
                        sources.getOrDefault(code, List.of())))
                .toList();
        return new EffectiveAccess(false, entries);
    }

    /** Effective set of a role on its own: direct ∪ active groups, closed (role detail view). */
    public static Set<String> resolveRole(RoleGrant role) {
        if (role.isSuperAdmin()) {
            return PermissionCatalog.codes();
        }
        return resolve(new AccessSnapshot(List.of(new RoleGrant(role.id(), role.code(), role.name(), true,
                role.permissions(), role.groups())), List.of(), Set.of(), Set.of())).granted();
    }

    private static Map<String, List<PermissionSource>> collectSources(AccessSnapshot snapshot) {
        Map<String, List<PermissionSource>> sources = new LinkedHashMap<>();
        Function<String, List<PermissionSource>> bucket = code -> new ArrayList<>();
        snapshot.roles().stream().filter(RoleGrant::active).forEach(role -> {
            role.permissions().stream().filter(PermissionCatalog::contains)
                    .forEach(code -> sources.computeIfAbsent(code, bucket).add(PermissionSource.role(role)));
            role.groups().stream().filter(GroupGrant::active).forEach(group -> group.permissions().stream()
                    .filter(PermissionCatalog::contains)
                    .forEach(code -> sources.computeIfAbsent(code, bucket).add(PermissionSource.roleGroup(group, role))));
        });
        snapshot.groups().stream().filter(GroupGrant::active).forEach(group -> group.permissions().stream()
                .filter(PermissionCatalog::contains)
                .forEach(code -> sources.computeIfAbsent(code, bucket).add(PermissionSource.userGroup(group))));
        snapshot.grants().stream().filter(PermissionCatalog::contains)
                .forEach(code -> sources.computeIfAbsent(code, bucket).add(PermissionSource.directGrant()));
        return sources;
    }
}
