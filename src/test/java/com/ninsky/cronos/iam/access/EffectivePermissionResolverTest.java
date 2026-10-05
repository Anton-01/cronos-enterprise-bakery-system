package com.ninsky.cronos.iam.access;

import com.ninsky.cronos.iam.permission.PermissionCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.ninsky.cronos.iam.permission.Permissions.*;
import static org.assertj.core.api.Assertions.assertThat;

class EffectivePermissionResolverTest {

    private static final GroupGrant FINANCE_GROUP = new GroupGrant(10, "FINANCE_READ", "Consulta financiera", true,
            Set.of(FINANCE_TAX_RATE_READ, FINANCE_CURRENCY_READ));
    private static final RoleGrant SALES = new RoleGrant(3, "SALES_MANAGER", "Gerente de ventas", true,
            Set.of(QUOTE_READ, QUOTE_APPROVE), List.of(FINANCE_GROUP));

    @Test
    void unionsRolesRoleGroupsUserGroupsAndGrantsWithSources() {
        var snapshot = new AccessSnapshot(List.of(SALES), List.of(new GroupGrant(11, "AUDIT", "Auditoría", true, Set.of(IAM_AUDIT_READ))),
                Set.of(RECIPE_READ), Set.of());

        EffectiveAccess access = EffectivePermissionResolver.resolve(snapshot);

        assertThat(access.granted()).containsExactlyInAnyOrder(QUOTE_READ, QUOTE_APPROVE, FINANCE_TAX_RATE_READ,
                FINANCE_CURRENCY_READ, IAM_AUDIT_READ, RECIPE_READ);
        EffectiveEntry tax = entry(access, FINANCE_TAX_RATE_READ);
        assertThat(tax.sources()).containsExactly(new PermissionSource(SourceType.ROLE_GROUP, 10L, "Consulta financiera", "Gerente de ventas"));
        assertThat(entry(access, IAM_AUDIT_READ).sources()).extracting(PermissionSource::type).containsExactly(SourceType.USER_GROUP);
        assertThat(entry(access, RECIPE_READ).sources()).extracting(PermissionSource::type).containsExactly(SourceType.DIRECT_GRANT);
    }

    @Test
    void denialWinsAndIsReportedWithItsSources() {
        var access = EffectivePermissionResolver.resolve(new AccessSnapshot(List.of(SALES), List.of(), Set.of(), Set.of(QUOTE_APPROVE)));

        EffectiveEntry approve = entry(access, QUOTE_APPROVE);
        assertThat(approve.granted()).isFalse();
        assertThat(approve.deniedExplicitly()).isTrue();
        assertThat(approve.sources()).hasSize(1);
    }

    @Test
    void denyingAReadDropsEveryDependentPermission() {
        var access = EffectivePermissionResolver.resolve(new AccessSnapshot(List.of(SALES), List.of(), Set.of(), Set.of(QUOTE_READ)));

        assertThat(access.granted()).doesNotContain(QUOTE_READ, QUOTE_APPROVE);
        assertThat(entry(access, QUOTE_APPROVE).granted()).isFalse();
    }

    @Test
    void inactiveRolesAndGroupsContributeNothing() {
        var inactiveGroup = new GroupGrant(10, "FINANCE_READ", "Consulta financiera", false, FINANCE_GROUP.permissions());
        var role = new RoleGrant(3, "SALES", "Ventas", true, Set.of(QUOTE_READ), List.of(inactiveGroup));
        var inactiveRole = new RoleGrant(4, "OTHER", "Otro", false, Set.of(RECIPE_READ), List.of());

        var access = EffectivePermissionResolver.resolve(new AccessSnapshot(List.of(role, inactiveRole), List.of(inactiveGroup), Set.of(), Set.of()));

        assertThat(access.granted()).containsExactly(QUOTE_READ);
    }

    @Test
    void activeSuperAdminHoldsTheWholeCatalogDespiteDenials() {
        var root = new RoleGrant(1, "SUPER_ADMIN", "Super administrador", true, Set.of(), List.of());

        var access = EffectivePermissionResolver.resolve(new AccessSnapshot(List.of(root), List.of(), Set.of(), Set.of(IAM_USER_READ)));

        assertThat(access.superAdmin()).isTrue();
        assertThat(access.granted()).isEqualTo(Set.copyOf(PermissionCatalog.codes()));
    }

    @Test
    void unknownCodesAreIgnoredAndResultIsDeterministic() {
        var snapshot = new AccessSnapshot(List.of(SALES), List.of(), Set.of("LEGACY.THING.READ"), Set.of());
        assertThat(EffectivePermissionResolver.resolve(snapshot)).isEqualTo(EffectivePermissionResolver.resolve(snapshot));
        assertThat(EffectivePermissionResolver.resolve(snapshot).granted()).doesNotContain("LEGACY.THING.READ");
    }

    @Test
    void addedAndRemovedCompareGrantedCodes() {
        var before = EffectivePermissionResolver.resolve(new AccessSnapshot(List.of(SALES), List.of(), Set.of(), Set.of()));
        var after = EffectivePermissionResolver.resolve(new AccessSnapshot(List.of(), List.of(), Set.of(RECIPE_READ), Set.of()));

        assertThat(EffectiveAccess.added(before, after)).containsExactly(RECIPE_READ);
        assertThat(EffectiveAccess.removed(before, after)).contains(QUOTE_APPROVE, FINANCE_TAX_RATE_READ);
    }

    @Test
    void roleEffectiveSetIncludesItsGroups() {
        assertThat(EffectivePermissionResolver.resolveRole(SALES)).contains(FINANCE_TAX_RATE_READ, QUOTE_APPROVE);
    }

    private static EffectiveEntry entry(EffectiveAccess access, String code) {
        return access.entries().stream().filter(e -> e.code().equals(code)).findFirst().orElseThrow();
    }
}
