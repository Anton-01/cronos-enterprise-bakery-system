package com.ninsky.cronos.account.profile.infrastructure;

import com.ninsky.cronos.account.avatar.domain.AvatarKey;
import com.ninsky.cronos.account.profile.domain.UserAccount;
import com.ninsky.cronos.account.shared.domain.DomainValidationException;
import com.ninsky.cronos.infrastructure.security.crypto.FieldEncryptionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * "My account" read model in one statement (performance-latency P1-5): the {@code users} row, the personal fields of
 * {@code user_profiles} and the role codes as an array, mapped straight to {@link UserAccount} without loading the JPA
 * user, its profile or its role collection. The sign-in query reuses {@link #ACCOUNT_COLUMNS} so the login response
 * can carry the same user summary without another round trip.
 */
@Slf4j
@Repository
public class UserAccountCustomRepository {

    /** Columns read by {@link #read(ResultSet)}; requires {@link #ACCOUNT_FROM} (aliases {@code u} and {@code p}). */
    public static final String ACCOUNT_COLUMNS = """
            u.id, u.username, u.email, p.first_name, p.last_name, p.phone_number, u.avatar_key, u.enabled,
            u.account_non_locked, u.two_factor_enabled, u.failed_login_attempts, u.locked_until, u.last_login_at,
            u.password_changed_at, u.created_at, u.updated_at, u.version,
            ARRAY(SELECT r.code FROM user_roles ur JOIN roles r ON r.id = ur.role_id WHERE ur.user_id = u.id) AS role_codes""";

    /** Source of {@link #ACCOUNT_COLUMNS}. */
    public static final String ACCOUNT_FROM = "users u LEFT JOIN user_profiles p ON p.user_id = u.id";

    /**
     * The account as stored: {@code email} and {@code phoneNumber} are still ciphertext, so a caller decides when (and
     * whether) to pay for decryption, e.g. only after the password matched.
     */
    public record StoredAccount(UUID id, String username, String encryptedEmail, String firstName, String lastName,
                                String encryptedPhoneNumber, String avatarKey, boolean enabled, boolean accountNonLocked,
                                boolean twoFactorEnabled, int failedLoginAttempts, LocalDateTime lockedUntil,
                                LocalDateTime lastLoginAt, LocalDateTime passwordChangedAt, Set<String> roles,
                                LocalDateTime createdAt, LocalDateTime updatedAt, long version) {

        /** The read model with email and phone decrypted (in memory, no I/O). */
        public UserAccount decrypt(FieldEncryptionService encryption) {
            return new UserAccount(id, username, encryption.decrypt(encryptedEmail), firstName, lastName,
                    encryption.decrypt(encryptedPhoneNumber), avatarKeyOf(id, avatarKey), enabled, accountNonLocked,
                    twoFactorEnabled, failedLoginAttempts, lockedUntil, lastLoginAt, passwordChangedAt, roles, createdAt,
                    updatedAt, version);
        }
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final FieldEncryptionService encryption;

    public UserAccountCustomRepository(NamedParameterJdbcTemplate jdbc, FieldEncryptionService encryption) {
        this.jdbc = jdbc;
        this.encryption = encryption;
    }

    /** The account of {@code userId}: one statement. */
    public Optional<UserAccount> findById(UUID userId) {
        return jdbc.query("SELECT " + ACCOUNT_COLUMNS + " FROM " + ACCOUNT_FROM + " WHERE u.id = :id",
                        new MapSqlParameterSource("id", userId), (rs, row) -> read(rs))
                .stream().findFirst().map(stored -> stored.decrypt(encryption));
    }

    /** Maps the {@link #ACCOUNT_COLUMNS} of the current row. */
    public static StoredAccount read(ResultSet rs) throws SQLException {
        return new StoredAccount(
                rs.getObject("id", UUID.class),
                rs.getString("username"),
                rs.getString("email"),
                rs.getString("first_name"),
                rs.getString("last_name"),
                rs.getString("phone_number"),
                rs.getString("avatar_key"),
                rs.getBoolean("enabled"),
                rs.getBoolean("account_non_locked"),
                rs.getBoolean("two_factor_enabled"),
                rs.getInt("failed_login_attempts"),
                local(rs.getTimestamp("locked_until")),
                local(rs.getTimestamp("last_login_at")),
                local(rs.getTimestamp("password_changed_at")),
                strings(rs.getArray("role_codes")),
                local(rs.getTimestamp("created_at")),
                local(rs.getTimestamp("updated_at")),
                rs.getLong("version"));
    }

    private static LocalDateTime local(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private static Set<String> strings(Array array) throws SQLException {
        if (array == null) {
            return Set.of();
        }
        Object[] values = (Object[]) array.getArray();
        return Arrays.stream(values).filter(java.util.Objects::nonNull).map(Object::toString)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /** A malformed stored key must not make the whole profile unreadable. */
    private static AvatarKey avatarKeyOf(UUID userId, String value) {
        try {
            return AvatarKey.ofNullable(value);
        } catch (DomainValidationException e) {
            log.warn("Ignoring malformed avatar_key on user {}", userId);
            return null;
        }
    }
}
