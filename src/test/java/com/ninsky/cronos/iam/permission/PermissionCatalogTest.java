package com.ninsky.cronos.iam.permission;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionCatalogTest {

    @Test
    void catalogIsAValidDagInCanonicalOrder() {
        PermissionCatalog.validate();
        assertThat(PermissionCatalog.all()).isSortedAccordingTo(PermissionDefinition.CANONICAL_ORDER);
        assertThat(PermissionCatalog.codes()).hasSize(54);
    }

    @Test
    void everyNonReadActionDependsOnItsOwnReadExceptStandaloneSettings() {
        PermissionCatalog.all().stream()
                .filter(d -> !d.isRead() && !d.code().equals(Permissions.FINANCE_SETTINGS_UPDATE))
                .forEach(d -> assertThat(d.dependsOn()).contains(d.module() + "." + d.resource() + ".READ"));
        assertThat(PermissionCatalog.find(Permissions.FINANCE_SETTINGS_UPDATE).orElseThrow().dependsOn())
                .containsExactlyInAnyOrder(Permissions.FINANCE_CURRENCY_READ, Permissions.FINANCE_TAX_RATE_READ);
    }

    @Test
    void actionsFollowReadCreateUpdateDeleteThenAlphabetical() {
        List<String> roleActions = PermissionCatalog.all().stream()
                .filter(d -> d.module().equals("IAM") && d.resource().equals("ROLE")).map(PermissionDefinition::action).toList();
        assertThat(roleActions).containsExactly("READ", "CREATE", "UPDATE", "DELETE", "MANAGE_MEMBERS");
    }

    @Test
    void withDependenciesClosesTransitively() {
        assertThat(PermissionCatalog.withDependencies(List.of(Permissions.IAM_USER_MANAGE_ACCESS)))
                .containsExactlyInAnyOrder(Permissions.IAM_USER_MANAGE_ACCESS, Permissions.IAM_USER_READ,
                        Permissions.IAM_ROLE_READ, Permissions.IAM_PERMISSION_GROUP_READ);
    }

    @Test
    void dropOrphansRemovesCodesWithMissingDependenciesAndUnknownCodes() {
        assertThat(PermissionCatalog.dropOrphans(List.of(Permissions.QUOTE_APPROVE, Permissions.RECIPE_READ, "NOPE.NOPE.READ")))
                .containsExactly(Permissions.RECIPE_READ);
    }
}
