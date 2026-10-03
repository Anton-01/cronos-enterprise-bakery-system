package com.ninsky.cronos.presentation.controller.support;

import com.ninsky.cronos.domain.model.audit.Actor;
import com.ninsky.cronos.infrastructure.security.CronosUserPrincipal;

/**
 * Who may maintain system-wide master data. Catalog writes restate conversions and costs for every
 * user, so they're limited to super admins and roles granted {@code MANAGE_CATALOGS} (V8); reads
 * stay open to any authenticated user (recipe screens need them).
 */
public final class CatalogAccess {

    public static final String CAN_MANAGE = "hasAnyAuthority('ROLE_SUPER_ADMIN', 'MANAGE_CATALOGS')";

    private CatalogAccess() {
    }

    public static Actor actorOf(CronosUserPrincipal principal) {
        return new Actor(principal.getId(), principal.getUsername());
    }
}
