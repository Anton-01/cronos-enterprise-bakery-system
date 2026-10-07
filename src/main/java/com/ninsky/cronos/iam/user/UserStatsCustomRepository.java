package com.ninsky.cronos.iam.user;

import com.ninsky.cronos.iam.shared.TenantTime;
import com.ninsky.cronos.iam.user.api.UserStats;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

/** Counters of {@code GET /iam/users/stats}; one pass over {@code users}. */
@Repository
@RequiredArgsConstructor
public class UserStatsCustomRepository {

    private static final int DORMANT_DAYS = 90;
    private static final int EXPIRING_DAYS = 30;

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public UserStats stats() {
        Map<UserStatus, Long> byStatus = new EnumMap<>(UserStatus.class);
        Arrays.stream(UserStatus.values()).forEach(s -> byStatus.put(s, 0L));
        long[] totals = new long[4];
        var params = new MapSqlParameterSource()
                .addValue("dormantBefore", TenantTime.nowLocal(clock).minusDays(DORMANT_DAYS))
                .addValue("today", TenantTime.today(clock))
                .addValue("expiringBy", TenantTime.today(clock).plusDays(EXPIRING_DAYS));
        jdbc.query("""
                SELECT status, count(*) AS total,
                       count(*) FILTER (WHERE two_factor_enabled) AS two_factor,
                       count(*) FILTER (WHERE last_login_at IS NULL) AS never_logged_in,
                       count(*) FILTER (WHERE status = 'ACTIVE' AND (last_login_at < :dormantBefore
                                         OR (last_login_at IS NULL AND created_at < :dormantBefore))) AS dormant,
                       count(*) FILTER (WHERE access_expires_at >= :today AND access_expires_at <= :expiringBy) AS expiring
                FROM users GROUP BY status""", params, (RowCallbackHandler) rs -> {
            byStatus.put(UserStatus.valueOf(rs.getString("status")), rs.getLong("total"));
            totals[0] += rs.getLong("two_factor");
            totals[1] += rs.getLong("never_logged_in");
            totals[2] += rs.getLong("dormant");
            totals[3] += rs.getLong("expiring");
        });
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        return new UserStats(total, byStatus, totals[0], totals[1], totals[2], totals[3]);
    }
}
