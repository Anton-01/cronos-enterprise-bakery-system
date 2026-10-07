package com.ninsky.cronos.iam.shared.migration;

import com.ninsky.cronos.iam.permission.PermissionCatalog;
import com.ninsky.cronos.iam.permission.PermissionCatalogCustomRepository;
import com.ninsky.cronos.iam.role.SystemRole;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * IAM seed (spec §13). Java so the permission rows come from the code registry, not from SQL.
 * Maps legacy access 1:1 so nobody loses access on deploy (§1.4.4).
 */
@Slf4j
@Component
@SuppressWarnings("java:S101") // Flyway derives the version from this class name.
public class V11__iam_seed extends BaseJavaMigration {

    private static final List<SodSeed> SOD_RULES = List.of(
            new SodSeed("SOD_QUOTE_CREATE_APPROVE", "WARNING",
                    "Crear y aprobar cotizaciones", "Create and approve quotes",
                    "Una misma persona no debería crear y aprobar sus propias cotizaciones",
                    "The same person should not create and approve their own quotes",
                    List.of(List.of("QUOTE.QUOTE.CREATE"), List.of("QUOTE.QUOTE.APPROVE"))),
            new SodSeed("SOD_ACCESS_ADMIN_AUDIT_EXPORT", "WARNING",
                    "Administrar accesos y exportar auditoría", "Administer access and export audit",
                    "Quien administra accesos no debería poder extraer la evidencia de auditoría",
                    "Whoever administers access should not be able to extract the audit evidence",
                    List.of(List.of("IAM.USER.MANAGE_ACCESS", "IAM.ROLE.UPDATE"), List.of("IAM.AUDIT.EXPORT"))),
            new SodSeed("SOD_POLICY_AND_ACCESS", "BLOCKING",
                    "Política, accesos y credenciales", "Policy, access and credentials",
                    "Debilitar controles, otorgar accesos y emitir credenciales concentrados en una sola persona",
                    "Weakening controls, granting access and issuing credentials concentrated in one person",
                    List.of(List.of("IAM.SECURITY_POLICY.UPDATE"), List.of("IAM.USER.MANAGE_ACCESS"), List.of("IAM.USER.RESET_CREDENTIALS"))),
            new SodSeed("SOD_TAX_AND_DEFAULTS", "WARNING",
                    "Definir y activar tasas fiscales", "Define and activate tax rates",
                    "Definir y activar tasas fiscales sin revisión",
                    "Defining and activating tax rates without review",
                    List.of(List.of("FINANCE.TAX_RATE.MANAGE"), List.of("FINANCE.SETTINGS.UPDATE"))));

    /** Legacy permission name → the catalog codes that replace it. */
    private static final List<LegacyMapping> LEGACY_PERMISSIONS = List.of(
            new LegacyMapping("VIEW_DASHBOARD", SystemRole.USER),
            new LegacyMapping("MANAGE_USERS", SystemRole.ADMIN),
            new LegacyMapping("ALL_ACCESS", SystemRole.ADMIN));

    @Override
    public void migrate(Context context) {
        JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(context.getConnection(), true));

        new PermissionCatalogCustomRepository(jdbc).synchronize("FLYWAY_V11");
        SystemRole.ALL.forEach(role -> upsertSystemRole(jdbc, role));
        mapLegacyAccess(jdbc);
        seedSodRules(jdbc);
        seedSecurityPolicy(jdbc);
        remapNavigation(jdbc);
        log.info("IAM seed applied: {} system roles, {} SoD rules", SystemRole.ALL.size(), SOD_RULES.size());
    }

    private void upsertSystemRole(JdbcTemplate jdbc, SystemRole role) {
        Integer existing = jdbc.queryForObject("SELECT count(*) FROM roles WHERE code = ?", Integer.class, role.code());
        if (existing == null || existing == 0) {
            jdbc.update("""
                    INSERT INTO roles (code, name, description, color, system, status, created_at, created_by)
                    VALUES (?, ?, ?, ?, TRUE, 'ACTIVE', now(), 'FLYWAY_V11')""",
                    role.code(), role.nameEs(), role.descriptionEs(), role.color());
        } else {
            jdbc.update("UPDATE roles SET name = ?, description = coalesce(description, ?), color = ?, system = TRUE, status = 'ACTIVE' WHERE code = ?",
                    role.nameEs(), role.descriptionEs(), role.color(), role.code());
        }
        grant(jdbc, "r.code = '" + role.code() + "'", role.permissions());
    }

    /** Custom roles keep today's capabilities: business modules were open to every authenticated user. */
    private void mapLegacyAccess(JdbcTemplate jdbc) {
        grant(jdbc, "NOT r.system", SystemRole.USER.permissions());
        LEGACY_PERMISSIONS.forEach(mapping -> grant(jdbc, """
                r.id IN (SELECT rp.role_id FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id
                       WHERE p.name = '%s') AND NOT r.system""".formatted(mapping.legacyName()), mapping.equivalent().permissions()));
        grant(jdbc, """
                r.id IN (SELECT rp.role_id FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id
                         WHERE p.name = 'MANAGE_CATALOGS') AND NOT r.system""", PermissionCatalog.matching("CATALOG", null, null));
        // Users without any role could use every business module; they become USERs.
        jdbc.update("""
                INSERT INTO user_roles (user_id, role_id)
                SELECT u.id, r.id FROM users u CROSS JOIN roles r
                WHERE r.code = 'USER' AND NOT EXISTS (SELECT 1 FROM user_roles ur WHERE ur.user_id = u.id)""");
    }

    /** Adds {@code codes} to every role matching {@code roleCondition} (SQL over alias {@code r}). */
    private void grant(JdbcTemplate jdbc, String roleCondition, Collection<String> codes) {
        if (codes.isEmpty()) {
            return;
        }
        String placeholders = String.join(",", Collections.nCopies(codes.size(), "?"));
        jdbc.update("""
                INSERT INTO role_permissions (role_id, permission_id)
                SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
                WHERE %s AND p.name IN (%s)
                ON CONFLICT DO NOTHING""".formatted(roleCondition, placeholders), codes.toArray());
    }

    private void seedSodRules(JdbcTemplate jdbc) {
        SOD_RULES.forEach(rule -> {
            jdbc.update("""
                    INSERT INTO sod_rules (code, name_es, name_en, description_es, description_en, severity)
                    VALUES (?, ?, ?, ?, ?, ?)""",
                    rule.code(), rule.nameEs(), rule.nameEn(), rule.descriptionEs(), rule.descriptionEn(), rule.severity());
            for (int set = 0; set < rule.sets().size(); set++) {
                for (String code : rule.sets().get(set)) {
                    jdbc.update("INSERT INTO sod_rule_sets (rule_code, set_index, permission_code) VALUES (?, ?, ?)",
                            rule.code(), set, code);
                }
            }
        });
    }

    private void seedSecurityPolicy(JdbcTemplate jdbc) {
        jdbc.update("""
                INSERT INTO security_policy (id, password_min_length, password_require_uppercase, password_require_lowercase,
                    password_require_digit, password_require_symbol, password_history, password_max_age_days,
                    max_failed_attempts, lockout_minutes, session_idle_minutes, session_absolute_hours,
                    max_concurrent_sessions, invitation_ttl_hours)
                VALUES (1, 12, TRUE, TRUE, TRUE, TRUE, 5, 90, 5, 15, 30, 12, 3, 72)""");
        jdbc.update("""
                INSERT INTO security_policy_2fa_roles (role_id)
                SELECT id FROM roles WHERE code IN ('SUPER_ADMIN', 'ADMIN')""");
    }

    /** Navigation entries were gated by the legacy permissions; point them at catalog codes. */
    private void remapNavigation(JdbcTemplate jdbc) {
        List.of(new String[]{"dashboard", "DASHBOARD.HOME.READ"},
                        new String[]{"catalog", "CATALOG.CATEGORY.READ"},
                        new String[]{"catalog_raw_materials", "INGREDIENT.INGREDIENT.READ"},
                        new String[]{"catalog_categories", "CATALOG.CATEGORY.READ"},
                        new String[]{"catalog_units", "CATALOG.UNIT_TYPE.READ"},
                        new String[]{"catalog_allergens", "CATALOG.ALLERGEN.READ"},
                        new String[]{"recipes", "RECIPE.RECIPE.READ"},
                        new String[]{"quotes", "QUOTE.QUOTE.READ"},
                        new String[]{"administration", "IAM.USER.READ"},
                        new String[]{"administration_users", "IAM.USER.READ"},
                        new String[]{"administration_roles", "IAM.ROLE.READ"})
                .forEach(entry -> jdbc.update("UPDATE menu_items SET required_permission = ? WHERE code = ?", entry[1], entry[0]));

        jdbc.update("""
                INSERT INTO menu_items (code, label_en, label_es, icon, path, parent_id, display_order, required_permission)
                SELECT v.code, v.label_en, v.label_es, v.icon, v.path, parent.id, v.display_order, v.required_permission
                FROM menu_items parent
                CROSS JOIN (VALUES
                    ('administration_permission_groups', 'Permission groups', 'Grupos de permisos', 'workspaces', '/admin/permission-groups', 3, 'IAM.PERMISSION_GROUP.READ'),
                    ('administration_audit_log', 'Audit log', 'Bitácora de auditoría', 'history', '/admin/audit-log', 4, 'IAM.AUDIT.READ'),
                    ('administration_security_policy', 'Security policy', 'Política de seguridad', 'policy', '/admin/security-policy', 5, 'IAM.SECURITY_POLICY.READ')
                ) AS v (code, label_en, label_es, icon, path, display_order, required_permission)
                WHERE parent.code = 'administration'""");
        jdbc.update("""
                INSERT INTO menu_items (code, label_en, label_es, icon, path, display_order, required_permission)
                VALUES ('finance', 'Finance', 'Finanzas', 'payments', '/finance', 6, 'FINANCE.CURRENCY.READ')""");
    }

    private record SodSeed(String code, String severity, String nameEs, String nameEn, String descriptionEs,
                           String descriptionEn, List<List<String>> sets) {
    }

    private record LegacyMapping(String legacyName, SystemRole equivalent) {
    }
}
