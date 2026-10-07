package com.ninsky.cronos.iam.user;

import com.ninsky.cronos.iam.shared.TenantTime;
import com.ninsky.cronos.infrastructure.security.crypto.BlindIndexService;
import com.ninsky.cronos.infrastructure.security.crypto.FieldEncryptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

/** Write side of IAM users over the legacy {@code users} + {@code user_profiles} tables. */
@Repository
@RequiredArgsConstructor
public class UserWriteCustomRepository {

    private static final String EMAIL_CONTEXT = "email";

    private final NamedParameterJdbcTemplate jdbc;
    private final FieldEncryptionService encryption;
    private final BlindIndexService blindIndex;

    /** New PENDING_ACTIVATION user with its profile row. */
    public void insert(UUID id, UserProfile profile, String passwordHash, boolean mustChangePassword, UUID actorId,
                       String actorUsername, Instant now) {
        var params = profileParams(id, profile, actorId, now)
                .addValue("password", passwordHash)
                .addValue("mustChange", mustChangePassword)
                .addValue("actorName", actorUsername);
        jdbc.update("""
                INSERT INTO users (id, username, email, email_blind_index, password, enabled, account_non_expired,
                                   account_non_locked, credentials_non_expired, email_verified, failed_login_attempts,
                                   password_needs_change, two_factor_enabled, created_at, created_by, created_by_id,
                                   job_title, department, employee_number, locale, status, access_expires_at,
                                   require_two_factor, access_version, version)
                VALUES (:id, :username, :email, :emailIndex, :password, TRUE, TRUE, TRUE, TRUE, FALSE, 0, :mustChange,
                        FALSE, :nowLocal, :actorName, :actor, :jobTitle, :department, :employeeNumber, :locale,
                        'PENDING_ACTIVATION', :accessExpiresAt, :requireTwoFactor, 0, 0)""", params);
        jdbc.update("""
                INSERT INTO user_profiles (id, user_id, first_name, last_name, phone_number, email_notifications,
                                           push_notifications, sms_notifications, created_at, created_by, language)
                VALUES (:profileId, :id, :firstName, :lastName, :phone, TRUE, FALSE, FALSE, :nowLocal, :actorName, :locale)""",
                params.addValue("profileId", UUID.randomUUID()));
    }

    /** Full profile replace; email change clears {@code email_verified}. @return false on a stale version */
    public boolean update(UUID id, UserProfile profile, boolean emailChanged, UUID actorId, Instant now, long expectedVersion) {
        var params = profileParams(id, profile, actorId, now)
                .addValue("emailChanged", emailChanged)
                .addValue("expected", expectedVersion);
        int rows = jdbc.update("""
                UPDATE users SET username = :username, email = :email, email_blind_index = :emailIndex,
                       email_verified = CASE WHEN :emailChanged THEN FALSE ELSE email_verified END,
                       job_title = :jobTitle, department = :department, employee_number = :employeeNumber,
                       locale = :locale, access_expires_at = :accessExpiresAt, require_two_factor = :requireTwoFactor,
                       updated_at = :nowLocal, updated_by_id = :actor, version = version + 1
                WHERE id = :id AND version = :expected""", params);
        if (rows == 0) {
            return false;
        }
        int profiles = jdbc.update("""
                UPDATE user_profiles SET first_name = :firstName, last_name = :lastName, phone_number = :phone,
                       updated_at = :nowLocal WHERE user_id = :id""", params);
        if (profiles == 0) {
            jdbc.update("""
                    INSERT INTO user_profiles (id, user_id, first_name, last_name, phone_number, email_notifications,
                                               push_notifications, sms_notifications, created_at, language)
                    VALUES (:profileId, :id, :firstName, :lastName, :phone, TRUE, FALSE, FALSE, :nowLocal, :locale)""",
                    params.addValue("profileId", UUID.randomUUID()));
        }
        return true;
    }

    public void requirePasswordChange(UUID id, UUID actorId, Instant now) {
        touch(id, "password_needs_change = TRUE", actorId, now, new MapSqlParameterSource());
    }

    /** Temporary password: stored hashed, change forced on next login. */
    public void setTemporaryPassword(UUID id, String passwordHash, UUID actorId, Instant now) {
        touch(id, "password = :password, password_needs_change = TRUE, password_changed_at = :nowLocal",
                actorId, now, new MapSqlParameterSource("password", passwordHash));
    }

    public void resetTwoFactor(UUID id, UUID actorId, Instant now) {
        touch(id, "two_factor_enabled = FALSE", actorId, now, new MapSqlParameterSource());
    }

    /** Bumps the version only (credential changes the client must observe). */
    public void touch(UUID id, UUID actorId, Instant now) {
        touch(id, "failed_login_attempts = failed_login_attempts", actorId, now, new MapSqlParameterSource());
    }

    private void touch(UUID id, String assignments, UUID actorId, Instant now, MapSqlParameterSource params) {
        jdbc.update("UPDATE users SET " + assignments + ", updated_at = :nowLocal, updated_by_id = coalesce(:actor, updated_by_id),"
                        + " version = version + 1 WHERE id = :id",
                params.addValue("id", id).addValue("actor", actorId).addValue("nowLocal", TenantTime.toLocal(now)));
    }

    private MapSqlParameterSource profileParams(UUID id, UserProfile p, UUID actorId, Instant now) {
        return new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("username", p.username())
                .addValue("email", encryption.encrypt(p.email()))
                .addValue("emailIndex", blindIndex.hmac(EMAIL_CONTEXT, p.email()))
                .addValue("firstName", p.firstName())
                .addValue("lastName", p.lastName())
                .addValue("phone", p.phoneNumber() == null ? null : encryption.encrypt(p.phoneNumber()))
                .addValue("jobTitle", p.jobTitle())
                .addValue("department", p.department())
                .addValue("employeeNumber", p.employeeNumber())
                .addValue("locale", p.locale())
                .addValue("accessExpiresAt", p.accessExpiresAt())
                .addValue("requireTwoFactor", p.requireTwoFactor())
                .addValue("actor", actorId)
                .addValue("nowLocal", TenantTime.toLocal(now));
    }

    public void setAvatarKey(UUID id, String key) {
        jdbc.update("UPDATE users SET avatar_key = :key WHERE id = :id", new MapSqlParameterSource("key", key).addValue("id", id));
    }
}
