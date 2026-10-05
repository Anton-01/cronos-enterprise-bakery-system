package com.ninsky.cronos.presentation.controller.support;

import com.ninsky.cronos.domain.model.audit.Actor;
import com.ninsky.cronos.domain.model.auth.EffectivePermissions;
import com.ninsky.cronos.infrastructure.security.CronosUserPrincipal;

/**
 * Who may maintain system-wide master data. Catalog writes restate conversions and costs for every
 * user, so they need the {@code MANAGE_CATALOGS} permission (V8) — granted to a role, or implied by
 * {@code SUPER_ADMIN} via {@link EffectivePermissions}, the same source the access token's
 * {@code permissions} claim is built from. Reads stay open to any authenticated user.
 */
public final class CatalogAccess {

    public static final String CAN_MANAGE = "hasAuthority('" + EffectivePermissions.MANAGE_CATALOGS + "')";

    // Per-catalog permission codes (spec §1.4.4); the legacy alias stays accepted during the transition.
    public static final String UNIT_TYPE_MANAGE = "hasAnyAuthority('CATALOG.UNIT_TYPE.MANAGE', '" + EffectivePermissions.MANAGE_CATALOGS + "')";
    public static final String MEASUREMENT_UNIT_MANAGE = "hasAnyAuthority('CATALOG.MEASUREMENT_UNIT.MANAGE', '" + EffectivePermissions.MANAGE_CATALOGS + "')";
    public static final String CATEGORY_MANAGE = "hasAnyAuthority('CATALOG.CATEGORY.MANAGE', '" + EffectivePermissions.MANAGE_CATALOGS + "')";
    public static final String ALLERGEN_MANAGE = "hasAnyAuthority('CATALOG.ALLERGEN.MANAGE', '" + EffectivePermissions.MANAGE_CATALOGS + "')";
    public static final String IMPORT_READ = "hasAnyAuthority('CATALOG.IMPORT.READ', '" + EffectivePermissions.MANAGE_CATALOGS + "')";
    public static final String IMPORT_EXECUTE = "hasAnyAuthority('CATALOG.IMPORT.EXECUTE', '" + EffectivePermissions.MANAGE_CATALOGS + "')";

    private CatalogAccess() {
    }

    public static Actor actorOf(CronosUserPrincipal principal) {
        return new Actor(principal.getId(), principal.getUsername());
    }
}
