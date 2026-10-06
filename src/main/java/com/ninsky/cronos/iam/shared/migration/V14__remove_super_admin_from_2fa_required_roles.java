package com.ninsky.cronos.iam.shared.migration;

import com.ninsky.cronos.iam.audit.AuditHasher;
import com.ninsky.cronos.iam.shared.Changes;
import com.ninsky.cronos.iam.shared.TenantTime;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Contract §8.3 break-glass rule: SUPER_ADMIN is never 2FA-mandatory. Removes it from the policy,
 * bumps the policy version and appends the audit row to the hash-chained ledger.
 */
@Component
@SuppressWarnings("java:S101") // Flyway derives the version from this class name.
public class V14__remove_super_admin_from_2fa_required_roles extends BaseJavaMigration {

    static final long CHAIN_LOCK_KEY = 7_426_001L;
    static final String REASON = "SUPER_ADMIN removed from 2FA-required roles (break-glass rule, doc §8.3)";

    @Override
    public void migrate(Context context) {
        JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(context.getConnection(), true));
        List<Long> before = roleIds(jdbc);
        int removed = jdbc.update("""
                DELETE FROM security_policy_2fa_roles
                WHERE role_id IN (SELECT id FROM roles WHERE code = 'SUPER_ADMIN')""");
        if (removed == 0) {
            return;
        }
        jdbc.update("UPDATE security_policy SET version = version + 1, updated_at = now() WHERE id = 1");
        audit(jdbc, AuditHasher.toJson(Changes.of("twoFactorRequiredRoleIds", before, roleIds(jdbc))));
    }

    private static List<Long> roleIds(JdbcTemplate jdbc) {
        return jdbc.queryForList("SELECT role_id FROM security_policy_2fa_roles ORDER BY role_id", Long.class);
    }

    private static void audit(JdbcTemplate jdbc, String changes) {
        LocalDateTime createdAt = TenantTime.nowLocal(Clock.systemUTC());
        jdbc.queryForList("SELECT pg_advisory_xact_lock(?)", CHAIN_LOCK_KEY);
        String prevHash = jdbc.queryForList("SELECT hash FROM audit_log WHERE hash IS NOT NULL ORDER BY id DESC LIMIT 1",
                String.class).stream().findFirst().orElse(null);
        String hash = AuditHasher.hash(prevHash, new AuditHasher.Material(createdAt, null, "SECURITY_POLICY_UPDATED",
                "SECURITY", "SUCCESS", "WARNING", "SECURITY_POLICY", "1", "Security policy", null, changes, REASON));
        jdbc.update("""
                INSERT INTO audit_log (actor_label, action, category, outcome, severity, target_type, target_id, target_label,
                                       changes, reason, created_at, prev_hash, hash)
                VALUES ('System migration', 'SECURITY_POLICY_UPDATED', 'SECURITY', 'SUCCESS', 'WARNING',
                        'SECURITY_POLICY', '1', 'Security policy', CAST(? AS jsonb), ?, ?, ?, ?)""",
                changes, REASON, createdAt, prevHash, hash);
    }
}
