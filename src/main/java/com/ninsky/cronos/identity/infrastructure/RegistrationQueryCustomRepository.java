package com.ninsky.cronos.identity.infrastructure;

import com.ninsky.cronos.identity.api.RegistrationStats;
import com.ninsky.cronos.infrastructure.security.crypto.BlindIndexService;
import com.ninsky.cronos.infrastructure.web.paging.PageQuery;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Read side of the review queue: search, counts and the same-IP signal, one query each. */
@Repository
public class RegistrationQueryCustomRepository {

    /** Sort whitelist of {@code GET /iam/registrations}. Email is encrypted, so it sorts by username. */
    public static final Map<String, String> SORTS = Map.of(
            "submittedAt", "r.submitted_at",
            "displayName", "lower(coalesce(nullif(trim(coalesce(p.first_name, '') || ' ' || coalesce(p.last_name, '')), ''), u.username))",
            "email", "lower(u.username)",
            "status", "r.status",
            "reviewedAt", "r.reviewed_at");

    /** Search criteria; every field optional. */
    public record Filter(String search, Set<String> statuses, Boolean emailVerified) {
    }

    private static final String FROM = """
             FROM registration_requests r
             JOIN users u ON u.id = r.user_id
             LEFT JOIN user_profiles p ON p.user_id = u.id
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final BlindIndexService blindIndex;

    public RegistrationQueryCustomRepository(NamedParameterJdbcTemplate jdbc, BlindIndexService blindIndex) {
        this.jdbc = jdbc;
        this.blindIndex = blindIndex;
    }

    /** Registration rows of one page. */
    public List<RegistrationCustomRepository.RegistrationRow> page(Filter filter, PageQuery page) {
        MapSqlParameterSource params = params(filter).addValue("limit", page.size()).addValue("offset", page.offset());
        return jdbc.query("SELECT r.*" + FROM + where(filter) + " ORDER BY " + page.orderBySql() + ", r.id LIMIT :limit OFFSET :offset",
                params, RegistrationCustomRepository.ROW);
    }

    public long count(Filter filter) {
        Long total = jdbc.queryForObject("SELECT count(*)" + FROM + where(filter), params(filter), Long.class);
        return total == null ? 0 : total;
    }

    /** Other registrations from the same IP in the 24 h before each one. */
    public Map<UUID, Long> sameIpCounts(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Long> counts = new HashMap<>();
        jdbc.query("""
                        SELECT r.id, (SELECT count(*) FROM registration_requests o
                                      WHERE o.ip_address = r.ip_address AND o.id <> r.id
                                        AND o.submitted_at >= r.submitted_at - INTERVAL '24 hours' AND o.submitted_at < r.submitted_at) AS same
                        FROM registration_requests r WHERE r.id IN (:ids) AND r.ip_address IS NOT NULL""",
                Map.of("ids", ids), (RowCallbackHandler) rs -> counts.put(rs.getObject("id", UUID.class), rs.getLong("same")));
        return counts;
    }

    /** Queue figures at {@code now}. */
    public RegistrationStats stats(Instant now) {
        return jdbc.queryForObject("""
                        SELECT count(*) FILTER (WHERE r.status = 'PENDING') AS pending,
                               count(*) FILTER (WHERE r.status = 'PENDING' AND NOT u.email_verified) AS pending_unverified,
                               count(*) FILTER (WHERE r.status = 'APPROVED' AND r.reviewed_at >= :since30) AS approved,
                               count(*) FILTER (WHERE r.status = 'REJECTED' AND r.reviewed_at >= :since30) AS rejected,
                               min(r.submitted_at) FILTER (WHERE r.status = 'PENDING') AS oldest,
                               percentile_cont(0.5) WITHIN GROUP (ORDER BY extract(epoch FROM r.reviewed_at - r.submitted_at) / 3600.0)
                                   FILTER (WHERE r.reviewed_at >= :since90 AND r.status IN ('APPROVED', 'REJECTED')) AS median_hours
                        FROM registration_requests r JOIN users u ON u.id = r.user_id""",
                new MapSqlParameterSource("since30", Timestamp.from(now.minusSeconds(30L * 86_400)))
                        .addValue("since90", Timestamp.from(now.minusSeconds(90L * 86_400))),
                (rs, row) -> new RegistrationStats(rs.getLong("pending"), rs.getLong("pending_unverified"), rs.getLong("approved"),
                        rs.getLong("rejected"), rs.getTimestamp("oldest") == null ? null : rs.getTimestamp("oldest").toInstant(),
                        rs.getObject("median_hours") == null ? null : rs.getDouble("median_hours")));
    }

    private String where(Filter filter) {
        List<String> clauses = new ArrayList<>();
        if (filter.statuses() != null && !filter.statuses().isEmpty()) {
            clauses.add("r.status IN (:statuses)");
        }
        if (filter.emailVerified() != null) {
            clauses.add("u.email_verified = :verified");
        }
        if (filter.search() != null && !filter.search().isBlank()) {
            clauses.add("""
                    (u.email_blind_index = :emailIndex OR unaccent(lower(u.username)) LIKE unaccent(:like)
                     OR unaccent(lower(coalesce(p.first_name, '') || ' ' || coalesce(p.last_name, ''))) LIKE unaccent(:like)
                     OR lower(r.reference) LIKE :like OR unaccent(lower(coalesce(r.business_name, ''))) LIKE unaccent(:like))""");
        }
        return clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses);
    }

    private MapSqlParameterSource params(Filter filter) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("statuses", filter.statuses())
                .addValue("verified", filter.emailVerified());
        if (filter.search() != null && !filter.search().isBlank()) {
            String term = filter.search().trim().toLowerCase(Locale.ROOT);
            params.addValue("like", "%" + term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%")
                    .addValue("emailIndex", blindIndex.hmac("email", term));
        }
        return params;
    }
}
