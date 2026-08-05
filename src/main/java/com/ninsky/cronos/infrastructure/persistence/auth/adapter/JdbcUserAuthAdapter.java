package com.ninsky.cronos.infrastructure.persistence.auth.adapter;

import com.ninsky.cronos.domain.model.auth.AuthUserProjection;
import com.ninsky.cronos.domain.port.auth.UserAuthLookupPort;
import com.ninsky.cronos.infrastructure.security.crypto.BlindIndexService;
import com.ninsky.cronos.infrastructure.security.crypto.FieldEncryptionService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Infrastructure adapter for {@link UserAuthLookupPort}. Sits on the highest-frequency path in
 * the app (login, and every authenticated request via {@code CustomUserDetailsService}) — fetches
 * only the columns needed to authenticate/authorize (no {@code UserProfile}, no audit columns, no
 * JPA entity graph), mirroring {@code JdbcErrorCatalogAdapter}'s style. Cached the same way the
 * JPA-based lookup it replaces was (keyed by the raw login id string) — this preserves, not
 * introduces, the pre-existing risk that a cache entry keyed by username isn't invalidated by an
 * eviction keyed by userId elsewhere (e.g. {@code UserService.changePassword}); not redesigned
 * here since that's a caching-strategy decision, not a mechanical refactor.
 */
@Repository
public class JdbcUserAuthAdapter implements UserAuthLookupPort {

    private static final String EMAIL_FIELD_CONTEXT = "email";

    /**
     * email is non-deterministic ciphertext (random IV per encryption) — the query matches on the
     * deterministic email_blind_index column instead; the raw email column is only ever read back
     * here to be decrypted in-process via {@link FieldEncryptionService} (this raw JdbcTemplate query
     * bypasses the JPA {@code @Convert} converter entirely, so decryption has to happen explicitly).
     */
    private static final String SELECT_USER = """
            SELECT id, username, email, password, enabled, account_non_locked,
                   account_non_expired, credentials_non_expired, two_factor_enabled, locked_until
            FROM users
            WHERE username = ? OR email_blind_index = ?
            """;

    private static final String SELECT_ROLES_AND_PERMISSIONS = """
            SELECT r.name AS role_name, p.name AS permission_name
            FROM roles r
            JOIN user_roles ur ON ur.role_id = r.id
            LEFT JOIN role_permissions rp ON rp.role_id = r.id
            LEFT JOIN permissions p ON p.id = rp.permission_id
            WHERE ur.user_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final FieldEncryptionService fieldEncryptionService;
    private final BlindIndexService blindIndexService;

    public JdbcUserAuthAdapter(JdbcTemplate jdbcTemplate, FieldEncryptionService fieldEncryptionService, BlindIndexService blindIndexService) {
        this.jdbcTemplate = jdbcTemplate;
        this.fieldEncryptionService = fieldEncryptionService;
        this.blindIndexService = blindIndexService;
    }

    @Override
    @Cacheable(value = "userAuth", key = "#loginId")
    public Optional<AuthUserProjection> findByUsernameOrEmail(String loginId) {
        String emailBlindIndex = blindIndexService.hmac(EMAIL_FIELD_CONTEXT, loginId);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(SELECT_USER, loginId, emailBlindIndex);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> row = rows.get(0);
        UUID id = (UUID) row.get("id");

        Set<String> roleNames = new HashSet<>();
        Set<String> permissionNames = new HashSet<>();
        jdbcTemplate.query(SELECT_ROLES_AND_PERMISSIONS, rs -> {
            roleNames.add(rs.getString("role_name"));
            String permissionName = rs.getString("permission_name");
            if (permissionName != null) {
                permissionNames.add(permissionName);
            }
        }, id);

        Timestamp lockedUntilTs = (Timestamp) row.get("locked_until");
        LocalDateTime lockedUntil = lockedUntilTs != null ? lockedUntilTs.toLocalDateTime() : null;

        return Optional.of(new AuthUserProjection(
                id,
                (String) row.get("username"),
                fieldEncryptionService.decrypt((String) row.get("email")),
                (String) row.get("password"),
                (boolean) row.get("enabled"),
                (boolean) row.get("account_non_locked"),
                (boolean) row.get("account_non_expired"),
                (boolean) row.get("credentials_non_expired"),
                (boolean) row.get("two_factor_enabled"),
                lockedUntil,
                roleNames,
                permissionNames
        ));
    }
}
