package com.ninsky.cronos.iam.signin;

import com.ninsky.cronos.iam.shared.TenantTime;
import com.ninsky.cronos.iam.user.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

/** Reads {@link AccountStanding} uncached, inside the caller's transaction. */
@Component
@RequiredArgsConstructor
public class AccountStandings {

    private final NamedParameterJdbcTemplate jdbc;

    public Optional<AccountStanding> find(UUID userId) {
        return jdbc.query("""
                        SELECT status, status_until, access_expires_at, password_needs_change, password_changed_at,
                               failed_login_attempts, locale
                        FROM users WHERE id = :id""",
                new MapSqlParameterSource("id", userId),
                (rs, i) -> new AccountStanding(userId, UserStatus.valueOf(rs.getString("status")),
                        Optional.ofNullable(rs.getTimestamp("status_until")).map(Timestamp::toInstant).orElse(null),
                        Optional.ofNullable(rs.getDate("access_expires_at")).map(Date::toLocalDate).orElse(null),
                        rs.getBoolean("password_needs_change"),
                        Optional.ofNullable(rs.getTimestamp("password_changed_at"))
                                .map(Timestamp::toLocalDateTime).map(TenantTime::toInstant).orElse(null),
                        rs.getInt("failed_login_attempts"), rs.getString("locale")))
                .stream().findFirst();
    }
}
