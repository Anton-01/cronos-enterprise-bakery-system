package com.ninsky.cronos.iam.role;

import com.ninsky.cronos.iam.permission.PermissionCatalog;

import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Stream;

import static com.ninsky.cronos.iam.permission.Permissions.*;

/** The seeded system roles (spec §13.2). SUPER_ADMIN holds every permission implicitly. */
public record SystemRole(String code, String nameEs, String descriptionEs, String color, SortedSet<String> permissions) {

    public static final String SUPER_ADMIN_CODE = "SUPER_ADMIN";

    private static final Set<String> BUSINESS_MODULES = Set.of("DASHBOARD", "RECIPE", "QUOTE", "INGREDIENT", "FIXED_COST");

    public static final SystemRole SUPER_ADMIN = new SystemRole(SUPER_ADMIN_CODE, "Super administrador",
            "Administrador raíz: todos los permisos, no editable", "#0f172a", new TreeSet<>());

    public static final SystemRole ADMIN = new SystemRole("ADMIN", "Administrador",
            "Administración de usuarios, catálogos y operación del negocio", "#dc2626", union(
            PermissionCatalog.matching("IAM", "USER", null),
            Set.of(IAM_ROLE_READ, IAM_PERMISSION_GROUP_READ, IAM_AUDIT_READ),
            PermissionCatalog.matching("CATALOG", null, null),
            financeReads(),
            business()));

    public static final SystemRole MANAGER = new SystemRole("MANAGER", "Gerente",
            "Operación del negocio, incluida la aprobación de cotizaciones", "#2563eb", union(
            business(),
            PermissionCatalog.matching("CATALOG", null, "READ"),
            financeReads()));

    public static final SystemRole USER = new SystemRole("USER", "Usuario",
            "Operación diaria: recetas, cotizaciones, ingredientes y costos fijos", "#16a34a", union(
            business().stream().filter(code -> !QUOTE_APPROVE.equals(code)).toList(),
            PermissionCatalog.matching("CATALOG", null, "READ"),
            financeReads()));

    public static final List<SystemRole> ALL = List.of(SUPER_ADMIN, ADMIN, MANAGER, USER);

    private static SortedSet<String> business() {
        return PermissionCatalog.all().stream()
                .filter(d -> BUSINESS_MODULES.contains(d.module()))
                .map(d -> d.code())
                .collect(TreeSet::new, TreeSet::add, TreeSet::addAll);
    }

    private static Set<String> financeReads() {
        return Set.of(FINANCE_CURRENCY_READ, FINANCE_TAX_RATE_READ);
    }

    @SafeVarargs
    private static SortedSet<String> union(java.util.Collection<String>... sets) {
        return PermissionCatalog.withDependencies(Stream.of(sets).flatMap(java.util.Collection::stream).toList());
    }
}
