package com.ninsky.cronos.domain.model.auth;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Permissions a user effectively holds: the ones granted through their roles plus the ones a role
 * implies by policy. Single source for both the {@code permissions} claim of the access token (what
 * the SPA reads to show/hide actions) and the authorities checked by {@code @PreAuthorize} — so the
 * client can never disagree with the server about what a user may do, even if an implied permission
 * was never (or no longer) assigned to the role in {@code role_permissions}.
 */
public final class EffectivePermissions {

    public static final String SUPER_ADMIN_ROLE = "SUPER_ADMIN";
    public static final String MANAGE_CATALOGS = "MANAGE_CATALOGS";

    /** Role name → permissions it always carries, regardless of its role_permissions rows. */
    private static final Map<String, Set<String>> IMPLIED_BY_ROLE = Map.of(SUPER_ADMIN_ROLE, Set.of(MANAGE_CATALOGS));

    private EffectivePermissions() {
    }

    /** Granted permissions first (their order is kept), then implied ones not already present; no duplicates. */
    public static List<String> of(Collection<String> roleNames, Collection<String> grantedPermissionNames) {
        Set<String> effective = new LinkedHashSet<>(grantedPermissionNames);
        roleNames.stream()
                .map(role -> IMPLIED_BY_ROLE.getOrDefault(role.toUpperCase(Locale.ROOT), Set.of()))
                .flatMap(Set::stream)
                .sorted()
                .forEach(effective::add);
        return List.copyOf(effective);
    }
}
