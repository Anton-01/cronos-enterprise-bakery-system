package com.ninsky.cronos.domain.model.auth;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EffectivePermissionsTest {

    @Test
    void superAdminAlwaysCarriesManageCatalogsEvenWhenTheRoleWasNeverGrantedIt() {
        assertThat(EffectivePermissions.of(List.of("SUPER_ADMIN"), List.of("ALL_ACCESS", "MANAGE_USERS")))
                .containsExactly("ALL_ACCESS", "MANAGE_USERS", "MANAGE_CATALOGS");
    }

    @Test
    void roleNameMatchingIsCaseInsensitiveAndNeverDuplicates() {
        assertThat(EffectivePermissions.of(List.of("super_admin"), List.of("MANAGE_CATALOGS")))
                .containsExactly("MANAGE_CATALOGS");
    }

    @Test
    void otherRolesOnlyGetWhatTheyWereGranted() {
        assertThat(EffectivePermissions.of(List.of("USER"), List.of("VIEW_DASHBOARD"))).containsExactly("VIEW_DASHBOARD");
        assertThat(EffectivePermissions.of(List.of("CATALOG_MANAGER"), List.of("MANAGE_CATALOGS"))).containsExactly("MANAGE_CATALOGS");
        assertThat(EffectivePermissions.of(List.of(), List.of())).isEmpty();
    }
}
