package com.ninsky.cronos.identity.infrastructure;

import com.ninsky.cronos.identity.domain.RegistrationStatus;
import com.ninsky.cronos.identity.domain.RejectReason;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** SQL of {@code registration_requests} (§3). */
@Repository
public class RegistrationCustomRepository {

    /** One registration row. */
    public record RegistrationRow(UUID id, String reference, UUID userId, String businessName, String message, RegistrationStatus status,
                                  String ipAddress, String userAgent, Instant submittedAt, Instant expiresAt, Instant reviewedAt,
                                  UUID reviewedBy, RejectReason rejectReason, String reviewComment, List<Long> grantedRoles,
                                  long version) {
        public RegistrationRow {
            grantedRoles = grantedRoles == null ? List.of() : List.copyOf(grantedRoles);
        }
    }

    /** What a new registration stores. */
    public record NewRegistration(UUID id, String reference, UUID userId, String businessName, String message, String ipAddress,
                                  String userAgent, Instant submittedAt, Instant expiresAt) {
    }

    static final RowMapper<RegistrationRow> ROW = (rs, n) -> new RegistrationRow(
            rs.getObject("id", UUID.class), rs.getString("reference"), rs.getObject("user_id", UUID.class),
            rs.getString("business_name"), rs.getString("message"), RegistrationStatus.valueOf(rs.getString("status")),
            rs.getString("ip_address"), rs.getString("user_agent"), instant(rs.getTimestamp("submitted_at")),
            instant(rs.getTimestamp("expires_at")), instant(rs.getTimestamp("reviewed_at")), rs.getObject("reviewed_by", UUID.class),
            rs.getString("reject_reason") == null ? null : RejectReason.valueOf(rs.getString("reject_reason")),
            rs.getString("review_comment"), longs(rs.getArray("granted_roles")), rs.getLong("version"));

    private final NamedParameterJdbcTemplate jdbc;

    public RegistrationCustomRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** {@code REG-{year}-{seq:06}}. */
    public String nextReference(int year) {
        Long seq = jdbc.queryForObject("SELECT nextval('registration_reference_seq')", Map.of(), Long.class);
        return "REG-%d-%06d".formatted(year, seq == null ? 0 : seq);
    }

    public void insert(NewRegistration registration) {
        jdbc.update("""
                        INSERT INTO registration_requests (id, reference, user_id, business_name, message, status, ip_address,
                                                           user_agent, submitted_at, expires_at)
                        VALUES (:id, :reference, :userId, :businessName, :message, 'PENDING', :ip, :ua, :submittedAt, :expiresAt)""",
                new MapSqlParameterSource()
                        .addValue("id", registration.id())
                        .addValue("reference", registration.reference())
                        .addValue("userId", registration.userId())
                        .addValue("businessName", registration.businessName())
                        .addValue("message", registration.message())
                        .addValue("ip", cut(registration.ipAddress(), 45))
                        .addValue("ua", cut(registration.userAgent(), 512))
                        .addValue("submittedAt", Timestamp.from(registration.submittedAt()))
                        .addValue("expiresAt", Timestamp.from(registration.expiresAt())));
    }

    /** The registration of an account, if it self-registered. */
    public Optional<RegistrationRow> findByUser(UUID userId) {
        return jdbc.query("SELECT * FROM registration_requests WHERE user_id = :userId", Map.of("userId", userId), ROW).stream().findFirst();
    }

    /** One registration. */
    public Optional<RegistrationRow> find(UUID id) {
        return jdbc.query("SELECT * FROM registration_requests WHERE id = :id", Map.of("id", id), ROW).stream().findFirst();
    }

    /** One registration, row-locked for the rest of the transaction. */
    public Optional<RegistrationRow> lock(UUID id) {
        return jdbc.query("SELECT * FROM registration_requests WHERE id = :id FOR UPDATE", Map.of("id", id), ROW).stream().findFirst();
    }

    /**
     * Records a decision when the row is still PENDING at {@code expectedVersion} (null skips the version check).
     *
     * @return false when another decision or edit got there first
     */
    public boolean decide(UUID id, RegistrationStatus status, Long expectedVersion, UUID reviewer, Instant now, RejectReason reason,
                          String comment, List<Long> roles) {
        return jdbc.update("""
                        UPDATE registration_requests SET status = :status, reviewed_at = :now, reviewed_by = :reviewer,
                               reject_reason = :reason, review_comment = :comment, granted_roles = CAST(:roles AS BIGINT[]),
                               version = version + 1
                        WHERE id = :id AND status = 'PENDING' AND (CAST(:expected AS BIGINT) IS NULL OR version = :expected)""",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("status", status.name())
                        .addValue("now", Timestamp.from(now))
                        .addValue("reviewer", reviewer)
                        .addValue("reason", reason == null ? null : reason.name())
                        .addValue("comment", comment)
                        .addValue("roles", "{" + String.join(",", roles.stream().map(String::valueOf).toList()) + "}")
                        .addValue("expected", expectedVersion)) == 1;
    }

    /** PENDING registrations whose {@code expires_at} passed, oldest first. */
    public List<RegistrationRow> expiredPending(Instant now, int limit) {
        return jdbc.query("""
                        SELECT * FROM registration_requests WHERE status = 'PENDING' AND expires_at < :now
                        ORDER BY expires_at LIMIT :limit""",
                new MapSqlParameterSource("now", Timestamp.from(now)).addValue("limit", limit), ROW);
    }

    static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static List<Long> longs(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        Object[] values = (Object[]) array.getArray();
        return Arrays.stream(values).map(value -> ((Number) value).longValue()).toList();
    }

    private static String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
