package com.ninsky.cronos.iam.policy;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** JDBC access to the singleton {@code security_policy} row and its 2FA role list. */
@Component
@RequiredArgsConstructor
public class SecurityPolicyStore {

    /** Spec §8 defaults, used only when the seed row is missing. */
    static final SecurityPolicy DEFAULTS = new SecurityPolicy(12, true, true, true, true, 5, 90, 5, 15, 30, 12, 3, 72,
            List.of(), null, null, 0);

    private final NamedParameterJdbcTemplate jdbc;

    public SecurityPolicy load() {
        List<Long> roleIds = jdbc.queryForList("SELECT role_id FROM security_policy_2fa_roles ORDER BY role_id", Map.of(), Long.class);
        return jdbc.query("SELECT * FROM security_policy WHERE id = 1", Map.of(), (rs, i) -> new SecurityPolicy(
                        rs.getInt("password_min_length"), rs.getBoolean("password_require_uppercase"),
                        rs.getBoolean("password_require_lowercase"), rs.getBoolean("password_require_digit"),
                        rs.getBoolean("password_require_symbol"), rs.getInt("password_history"),
                        rs.getInt("password_max_age_days"), rs.getInt("max_failed_attempts"), rs.getInt("lockout_minutes"),
                        rs.getInt("session_idle_minutes"), rs.getInt("session_absolute_hours"),
                        rs.getInt("max_concurrent_sessions"), rs.getInt("invitation_ttl_hours"), roleIds,
                        Optional.ofNullable(rs.getTimestamp("updated_at")).map(Timestamp::toInstant).orElse(null),
                        rs.getObject("updated_by", UUID.class), rs.getLong("version")))
                .stream().findFirst().orElse(DEFAULTS);
    }

    /** Writes every field when the row is still at {@code expectedVersion}; false otherwise. */
    public boolean update(SecurityPolicy policy, long expectedVersion) {
        int rows = jdbc.update("""
                UPDATE security_policy SET password_min_length = :minLength, password_require_uppercase = :upper,
                       password_require_lowercase = :lower, password_require_digit = :digit, password_require_symbol = :symbol,
                       password_history = :history, password_max_age_days = :maxAge, max_failed_attempts = :attempts,
                       lockout_minutes = :lockout, session_idle_minutes = :idle, session_absolute_hours = :absolute,
                       max_concurrent_sessions = :concurrent, invitation_ttl_hours = :invitationTtl,
                       version = version + 1, updated_at = :updatedAt, updated_by = :updatedBy
                WHERE id = 1 AND version = :expected""", parameters(policy).addValue("expected", expectedVersion));
        if (rows == 0) {
            return false;
        }
        jdbc.update("DELETE FROM security_policy_2fa_roles", Map.of());
        jdbc.batchUpdate("INSERT INTO security_policy_2fa_roles (role_id) VALUES (:id)",
                policy.twoFactorRequiredRoleIds().stream().distinct()
                        .map(id -> new MapSqlParameterSource("id", id)).toArray(SqlParameterSource[]::new));
        return true;
    }

    /** The subset of {@code ids} that are existing ACTIVE roles. */
    public Set<Long> activeRoleIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(jdbc.queryForList("SELECT id FROM roles WHERE id IN (:ids) AND status = 'ACTIVE'",
                new MapSqlParameterSource("ids", Set.copyOf(ids)), Long.class));
    }

    private static MapSqlParameterSource parameters(SecurityPolicy p) {
        return new MapSqlParameterSource()
                .addValue("minLength", p.passwordMinLength())
                .addValue("upper", p.passwordRequireUppercase())
                .addValue("lower", p.passwordRequireLowercase())
                .addValue("digit", p.passwordRequireDigit())
                .addValue("symbol", p.passwordRequireSymbol())
                .addValue("history", p.passwordHistory())
                .addValue("maxAge", p.passwordMaxAgeDays())
                .addValue("attempts", p.maxFailedAttempts())
                .addValue("lockout", p.lockoutMinutes())
                .addValue("idle", p.sessionIdleMinutes())
                .addValue("absolute", p.sessionAbsoluteHours())
                .addValue("concurrent", p.maxConcurrentSessions())
                .addValue("invitationTtl", p.invitationTtlHours())
                .addValue("updatedAt", p.updatedAt() == null ? null : Timestamp.from(p.updatedAt()))
                .addValue("updatedBy", p.updatedBy());
    }
}
