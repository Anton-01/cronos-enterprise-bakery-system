package com.ninsky.cronos.identity.infrastructure;

import com.ninsky.cronos.account.profile.infrastructure.UserAccountCustomRepository;
import com.ninsky.cronos.account.profile.infrastructure.UserAccountCustomRepository.StoredAccount;
import com.ninsky.cronos.iam.shared.TenantTime;
import com.ninsky.cronos.iam.signin.AccountStanding;
import com.ninsky.cronos.iam.user.UserStatus;
import com.ninsky.cronos.identity.domain.LoginIdentifier;
import com.ninsky.cronos.identity.domain.RegistrationStatus;
import com.ninsky.cronos.infrastructure.security.crypto.BlindIndexService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * SQL of the sign-in critical path (performance-latency P0-5): one read with everything the decision needs, and the
 * session plus refresh token written by one statement inside the sign-in transaction.
 */
@Repository
public class SignInCustomRepository {

    private static final String EMAIL_FIELD_CONTEXT = "email";

    /**
     * Account summary ({@link UserAccountCustomRepository#ACCOUNT_COLUMNS}), password hash, lifecycle state, access
     * version, whether 2FA is required (per-user flag or a policy-listed role, never SUPER_ADMIN) and the
     * self-registration status, in one round trip.
     */
    private static final String SELECT = "SELECT " + UserAccountCustomRepository.ACCOUNT_COLUMNS + """
            ,
                   u.password, u.status, u.status_until, u.access_expires_at, u.password_needs_change, u.locale,
                   u.email_verified, u.access_version,
                   u.require_two_factor OR EXISTS (
                       SELECT 1 FROM user_roles rq JOIN roles rr ON rr.id = rq.role_id
                       WHERE rq.user_id = u.id AND rr.status = 'ACTIVE' AND rr.code <> 'SUPER_ADMIN' AND rr.id = ANY (:roleIds)
                   ) AS two_factor_required,
                   reg.status AS registration_status
            FROM """ + " " + UserAccountCustomRepository.ACCOUNT_FROM + """

            LEFT JOIN LATERAL (SELECT status FROM registration_requests WHERE user_id = u.id LIMIT 1) reg ON TRUE
            """;

    /** Everything one sign-in decision needs. {@code registrationStatus} is null for accounts not self-registered. */
    public record SignInRow(StoredAccount account, String passwordHash, UserStatus status, Instant statusUntil,
                            LocalDate accessExpiresAt, boolean passwordNeedsChange, String locale, boolean emailVerified,
                            long accessVersion, boolean twoFactorRequired, RegistrationStatus registrationStatus) {

        public UUID userId() {
            return account.id();
        }

        /** The lifecycle facts in the shape the sign-in rules use. */
        public AccountStanding standing() {
            Instant passwordChangedAt = account.passwordChangedAt() == null ? null : TenantTime.toInstant(account.passwordChangedAt());
            return new AccountStanding(account.id(), status, statusUntil, accessExpiresAt, passwordNeedsChange, passwordChangedAt,
                    account.failedLoginAttempts(), locale);
        }

        /** Mandatory 2FA not enrolled yet (contract §8.1). */
        public boolean mustEnrolTwoFactor() {
            return twoFactorRequired && !account.twoFactorEnabled();
        }
    }

    /** A new server session and its first refresh token (stored as a digest). */
    public record NewSession(UUID sessionId, UUID userId, String deviceId, String ipAddress, String userAgent, String browser,
                             String operatingSystem, String device, String location, LocalDateTime createdAt,
                             LocalDateTime expiresAt, String dpopJkt, boolean rememberMe, String refreshTokenHash,
                             LocalDateTime refreshTokenExpiresAt) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final BlindIndexService blindIndex;

    public SignInCustomRepository(NamedParameterJdbcTemplate jdbc, BlindIndexService blindIndex) {
        this.jdbc = jdbc;
        this.blindIndex = blindIndex;
    }

    /** By typed identifier: an email matches the blind index, anything else {@code lower(username)}. */
    public Optional<SignInRow> find(LoginIdentifier identifier, Collection<Long> twoFactorRoleIds) {
        MapSqlParameterSource params = new MapSqlParameterSource("roleIds", twoFactorRoleIds.toArray(Long[]::new));
        String where;
        if (identifier.email()) {
            where = "WHERE u.email_blind_index = :blind";
            params.addValue("blind", blindIndex.hmac(EMAIL_FIELD_CONTEXT, identifier.value()));
        } else {
            where = "WHERE lower(u.username) = :login";
            params.addValue("login", identifier.value());
        }
        return jdbc.query(SELECT + where, params, (rs, row) -> read(rs)).stream().findFirst();
    }

    /** By id (second step of a two-step sign-in). */
    public Optional<SignInRow> findById(UUID userId, Collection<Long> twoFactorRoleIds) {
        MapSqlParameterSource params = new MapSqlParameterSource("roleIds", twoFactorRoleIds.toArray(Long[]::new))
                .addValue("id", userId);
        return jdbc.query(SELECT + "WHERE u.id = :id", params, (rs, row) -> read(rs)).stream().findFirst();
    }

    /** Inserts the session and its refresh token in one statement (a data-modifying CTE, one round trip). */
    public void insertSession(NewSession session) {
        jdbc.update("""
                        WITH s AS (
                            INSERT INTO user_sessions (id, user_id, session_token, device_id, ip_address, user_agent, browser,
                                                       operating_system, device, location, is_active, created_at,
                                                       last_activity_at, expires_at, dpop_jkt, remember_me)
                            VALUES (:sessionId, :userId, :sessionToken, :deviceId, :ip, :userAgent, :browser, :os, :device,
                                    :location, TRUE, :now, :now, :sessionEnd, :dpopJkt, :rememberMe)
                            RETURNING id)
                        INSERT INTO refresh_tokens (id, token, user_id, session_id, expires_at, created_at, revoked, ip_address,
                                                    user_agent)
                        SELECT :tokenId, :tokenHash, :userId, s.id, :tokenExpiry, :now, FALSE, :ip, :userAgent FROM s""",
                new MapSqlParameterSource()
                        .addValue("sessionId", session.sessionId())
                        .addValue("userId", session.userId())
                        .addValue("sessionToken", UUID.randomUUID().toString())
                        .addValue("deviceId", cut(session.deviceId()))
                        .addValue("ip", cut(session.ipAddress()))
                        .addValue("userAgent", cut(session.userAgent()))
                        .addValue("browser", cut(session.browser()))
                        .addValue("os", cut(session.operatingSystem()))
                        .addValue("device", cut(session.device()))
                        .addValue("location", cut(session.location()))
                        .addValue("now", session.createdAt())
                        .addValue("sessionEnd", session.expiresAt())
                        .addValue("dpopJkt", session.dpopJkt())
                        .addValue("rememberMe", session.rememberMe())
                        .addValue("tokenId", UUID.randomUUID())
                        .addValue("tokenHash", session.refreshTokenHash())
                        .addValue("tokenExpiry", session.refreshTokenExpiresAt()));
    }

    /** Stores a hash re-computed with the configured bcrypt cost after a successful match. */
    public void rehashPassword(UUID userId, String hash) {
        jdbc.update("UPDATE users SET password = :hash WHERE id = :id", new MapSqlParameterSource("id", userId).addValue("hash", hash));
    }

    private static SignInRow read(ResultSet rs) throws SQLException {
        Timestamp statusUntil = rs.getTimestamp("status_until");
        java.sql.Date accessExpires = rs.getDate("access_expires_at");
        String registration = rs.getString("registration_status");
        return new SignInRow(
                UserAccountCustomRepository.read(rs),
                rs.getString("password"),
                UserStatus.valueOf(rs.getString("status")),
                statusUntil == null ? null : statusUntil.toInstant(),
                accessExpires == null ? null : accessExpires.toLocalDate(),
                rs.getBoolean("password_needs_change"),
                rs.getString("locale"),
                rs.getBoolean("email_verified"),
                rs.getLong("access_version"),
                rs.getBoolean("two_factor_required"),
                registration == null ? null : RegistrationStatus.valueOf(registration));
    }

    /** Session columns are VARCHAR(255). */
    private static String cut(String value) {
        return value == null || value.length() <= 255 ? value : value.substring(0, 255);
    }
}
