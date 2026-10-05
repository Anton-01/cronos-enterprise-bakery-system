package com.ninsky.cronos.iam.permission;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * Writes {@link PermissionCatalog} into the {@code permissions} table: inserts new codes, refreshes
 * module/resource/action/risk/dependencies, and marks codes no longer in the registry as deprecated
 * (rows referenced by roles are never deleted).
 */
public class PermissionCatalogWriter {

    private final JdbcTemplate jdbc;

    public PermissionCatalogWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public SyncResult synchronize(String actor) {
        List<PermissionDefinition> definitions = PermissionCatalog.all();
        int[] upserts = jdbc.batchUpdate("""
                INSERT INTO permissions (name, description, module, resource, action, risk, deprecated, created_at, created_by)
                VALUES (?, ?, ?, ?, ?, ?, FALSE, now(), ?)
                ON CONFLICT (name) DO UPDATE SET module = EXCLUDED.module, resource = EXCLUDED.resource,
                    action = EXCLUDED.action, risk = EXCLUDED.risk, deprecated = FALSE,
                    description = coalesce(permissions.description, EXCLUDED.description),
                    updated_at = now(), updated_by = EXCLUDED.created_by
                WHERE permissions.module IS DISTINCT FROM EXCLUDED.module OR permissions.resource IS DISTINCT FROM EXCLUDED.resource
                   OR permissions.action IS DISTINCT FROM EXCLUDED.action OR permissions.risk IS DISTINCT FROM EXCLUDED.risk
                   OR permissions.deprecated""",
                definitions.stream().map(d -> new Object[]{d.code(), d.code(), d.module(), d.resource(), d.action(), d.risk().name(), actor}).toList());

        jdbc.update("DELETE FROM permission_dependencies");
        jdbc.batchUpdate("INSERT INTO permission_dependencies (code, depends_on) VALUES (?, ?)",
                definitions.stream().flatMap(d -> d.dependsOn().stream().map(dep -> new Object[]{d.code(), dep})).toList());

        String placeholders = String.join(",", java.util.Collections.nCopies(definitions.size(), "?"));
        int deprecated = jdbc.update("UPDATE permissions SET deprecated = TRUE, updated_at = now() WHERE NOT deprecated AND name NOT IN (" + placeholders + ")",
                definitions.stream().map(PermissionDefinition::code).toArray());
        return new SyncResult(java.util.Arrays.stream(upserts).filter(count -> count > 0).count(), deprecated);
    }

    public record SyncResult(long upserted, int deprecated) {
    }
}
