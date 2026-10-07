package com.ninsky.cronos.iam.policy;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** SQL deciding whether a user must use 2FA (per-user flag or a policy-listed role). */
@Repository
@RequiredArgsConstructor
public class TwoFactorRequirementCustomRepository {

    /** Roles that count: ACTIVE, listed in the policy, never SUPER_ADMIN (§8.3). */
    private static final String REQUIRING_ROLES = """
            FROM user_roles ur JOIN roles r ON r.id = ur.role_id
            WHERE ur.user_id = u.id AND r.status = 'ACTIVE' AND r.code <> 'SUPER_ADMIN' AND r.id = ANY (:roleIds)""";

    private final NamedParameterJdbcTemplate jdbc;

    /** Whether 2FA is required and enabled; empty for an unknown user. */
    public record State(boolean required, boolean enabled) {
    }

    public Optional<State> state(UUID userId, Collection<Long> requiredRoleIds) {
        return jdbc.query("SELECT u.require_two_factor OR EXISTS (SELECT 1 " + REQUIRING_ROLES + ") AS required, "
                        + "u.two_factor_enabled AS enabled FROM users u WHERE u.id = :id",
                parameters(userId, requiredRoleIds), (rs, i) -> new State(rs.getBoolean("required"), rs.getBoolean("enabled")))
                .stream().findFirst();
    }

    /** Names of the user's roles that require 2FA, alphabetical. */
    public List<String> requiringRoleNames(UUID userId, Collection<Long> requiredRoleIds) {
        return jdbc.queryForList("SELECT r.name " + REQUIRING_ROLES.replace("u.id", ":id") + " ORDER BY lower(r.name)",
                parameters(userId, requiredRoleIds), String.class);
    }

    private static MapSqlParameterSource parameters(UUID userId, Collection<Long> requiredRoleIds) {
        return new MapSqlParameterSource("id", userId).addValue("roleIds", requiredRoleIds.toArray(Long[]::new));
    }
}
