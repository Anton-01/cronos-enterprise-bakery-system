package com.ninsky.cronos.quote.infrastructure;

import com.ninsky.cronos.domain.entity.enums.QuoteStatus;
import com.ninsky.cronos.infrastructure.persistence.jdbc.JdbcPages;
import com.ninsky.cronos.infrastructure.security.crypto.FieldEncryptionService;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Read side of quotes: ids of a page, figures for the stats and the candidates of the expiry jobs. Client fields are
 * encrypted at rest, so name/phone/email matching happens on the decrypted candidates in the service.
 */
@Repository
public class QuoteQueryCustomRepository {

    /** Sort whitelist of {@code GET /quotes} (API field → SQL). clientName sorts in memory (encrypted). */
    public static final Map<String, String> SORTS = Map.of(
            "createdAt", "q.created_at",
            "quoteNumber", "q.quote_number",
            "clientName", "q.created_at",
            "total", "q.total",
            "eventDate", "q.event_date",
            "validUntil", "q.valid_until");

    /** Status figures of one owner. */
    public record StatusCount(QuoteStatus status, String currency, long count, BigDecimal total) {
    }

    /** Decided quotes of the conversion window. */
    public record Decisions(long accepted, long rejected, long expired) {
    }

    /**
     * One row of the quotes grid, straight from {@code quotes} (no aggregate, no items). Client fields are decrypted in
     * memory; email and phone are only read for the in-memory search.
     */
    public record SummaryRow(UUID id, String quoteNumber, String clientName, String clientEmail, String clientPhone, BigDecimal total,
                             String currency, QuoteStatus status, LocalDateTime createdAt, String publicToken, boolean priceReviewRequired,
                             LocalDateTime validUntil, LocalDate eventDate, Integer viewsCount, UUID calendarEventId) {
    }

    private static final String SUMMARY_COLUMNS = "q.id, q.quote_number, q.client_name, q.client_email, q.client_phone, q.total, "
            + "q.currency, q.status, q.created_at, q.public_token, q.price_review_required, q.valid_until, q.event_date, q.views_count, "
            + "q.calendar_event_id";

    private final NamedParameterJdbcTemplate jdbc;
    private final FieldEncryptionService encryption;

    public QuoteQueryCustomRepository(NamedParameterJdbcTemplate jdbc, FieldEncryptionService encryption) {
        this.jdbc = jdbc;
        this.encryption = encryption;
    }

    /**
     * One page of the owner's live quotes and the total, in one statement (performance-latency P1-2/P1-3). With
     * {@code limit} null every matching row is returned (in-memory search over the decrypted client fields).
     */
    public JdbcPages.Rows<SummaryRow> summaries(UUID ownerId, Set<QuoteStatus> statuses, String orderBy, Integer limit, long offset) {
        String where = " FROM quotes q WHERE q.user_id = :owner AND q.deleted_at IS NULL"
                + (statuses.isEmpty() ? "" : " AND q.status IN (:statuses)");
        MapSqlParameterSource params = new MapSqlParameterSource("owner", ownerId)
                .addValue("statuses", statuses.stream().map(Enum::name).toList())
                .addValue("limit", limit).addValue("offset", offset);
        boolean searching = limit == null;
        String sql = "SELECT " + SUMMARY_COLUMNS + ", " + JdbcPages.TOTAL_COLUMN + where + " ORDER BY " + orderBy + ", q.id"
                + (searching ? "" : " LIMIT :limit OFFSET :offset");
        return JdbcPages.rows(jdbc, sql, params, searching ? 0 : offset, (rs, row) -> new SummaryRow(
                rs.getObject("id", UUID.class), rs.getString("quote_number"), encryption.decrypt(rs.getString("client_name")),
                searching ? encryption.decrypt(rs.getString("client_email")) : null,
                searching ? encryption.decrypt(rs.getString("client_phone")) : null,
                rs.getBigDecimal("total"), rs.getString("currency"), QuoteStatus.valueOf(rs.getString("status")),
                localDateTime(rs.getTimestamp("created_at")), rs.getString("public_token"), rs.getBoolean("price_review_required"),
                localDateTime(rs.getTimestamp("valid_until")), rs.getObject("event_date", LocalDate.class),
                rs.getObject("views_count", Integer.class), rs.getObject("calendar_event_id", UUID.class)),
                "SELECT count(*)" + where);
    }

    private static LocalDateTime localDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    /** Ids of the owner's live quotes with {@code statuses} (all when empty), in {@code orderBy} order. */
    public List<UUID> ids(UUID ownerId, Set<QuoteStatus> statuses, String orderBy, Integer limit, long offset) {
        String sql = "SELECT q.id FROM quotes q WHERE q.user_id = :owner AND q.deleted_at IS NULL"
                + (statuses.isEmpty() ? "" : " AND q.status IN (:statuses)")
                + " ORDER BY " + orderBy + ", q.id" + (limit == null ? "" : " LIMIT :limit OFFSET :offset");
        return jdbc.queryForList(sql, new MapSqlParameterSource("owner", ownerId)
                .addValue("statuses", statuses.stream().map(Enum::name).toList())
                .addValue("limit", limit).addValue("offset", offset), UUID.class);
    }

    public long count(UUID ownerId, Set<QuoteStatus> statuses) {
        Long total = jdbc.queryForObject("SELECT count(*) FROM quotes q WHERE q.user_id = :owner AND q.deleted_at IS NULL"
                        + (statuses.isEmpty() ? "" : " AND q.status IN (:statuses)"),
                new MapSqlParameterSource("owner", ownerId).addValue("statuses", statuses.stream().map(Enum::name).toList()), Long.class);
        return total == null ? 0 : total;
    }

    /** Every figure of {@code GET /quotes/stats} for one owner, from one statement. */
    public record Stats(Map<QuoteStatus, Long> counts, Map<QuoteStatus, BigDecimal> totalsInCurrency, BigDecimal acceptedInWindow,
                        Decisions decisions, long expiringSoon) {
    }

    /**
     * One aggregate (performance-latency P1-5): counts and totals per status (totals only in {@code currency}), plus the
     * window figures as window sums over the grouped rows.
     */
    public Stats stats(UUID ownerId, String currency, Instant acceptedSince, Instant decidedSince, LocalDateTime decidedSinceLocal,
                       LocalDateTime now, LocalDateTime expiringUntil) {
        Map<QuoteStatus, Long> counts = new java.util.EnumMap<>(QuoteStatus.class);
        Map<QuoteStatus, BigDecimal> totals = new java.util.EnumMap<>(QuoteStatus.class);
        BigDecimal[] accepted = {BigDecimal.ZERO};
        long[] figures = new long[4];
        jdbc.query("""
                        SELECT status, count(*) AS n,
                               coalesce(sum(total) FILTER (WHERE upper(currency) = upper(:currency)), 0) AS currency_total,
                               coalesce(sum(sum(total) FILTER (WHERE status = 'ACCEPTED' AND accepted_at >= :acceptedSince
                                                               AND upper(currency) = upper(:currency))) OVER (), 0) AS accepted_total,
                               sum(count(*) FILTER (WHERE status = 'ACCEPTED' AND accepted_at >= :decidedSince)) OVER () AS accepted,
                               sum(count(*) FILTER (WHERE status = 'REJECTED' AND rejected_at >= :decidedSince)) OVER () AS rejected,
                               sum(count(*) FILTER (WHERE status = 'EXPIRED' AND valid_until >= :decidedSinceLocal)) OVER () AS expired,
                               sum(count(*) FILTER (WHERE status IN ('SENT', 'VIEWED') AND valid_until >= :now
                                                     AND valid_until < :expiringUntil)) OVER () AS expiring
                        FROM quotes WHERE user_id = :owner AND deleted_at IS NULL GROUP BY status""",
                new MapSqlParameterSource("owner", ownerId).addValue("currency", currency)
                        .addValue("acceptedSince", Timestamp.from(acceptedSince)).addValue("decidedSince", Timestamp.from(decidedSince))
                        .addValue("decidedSinceLocal", decidedSinceLocal).addValue("now", now).addValue("expiringUntil", expiringUntil),
                (RowCallbackHandler) rs -> {
                    QuoteStatus status = QuoteStatus.valueOf(rs.getString("status"));
                    counts.put(status, rs.getLong("n"));
                    totals.put(status, rs.getBigDecimal("currency_total"));
                    accepted[0] = rs.getBigDecimal("accepted_total");
                    figures[0] = rs.getLong("accepted");
                    figures[1] = rs.getLong("rejected");
                    figures[2] = rs.getLong("expired");
                    figures[3] = rs.getLong("expiring");
                });
        return new Stats(Map.copyOf(counts), Map.copyOf(totals), accepted[0], new Decisions(figures[0], figures[1], figures[2]), figures[3]);
    }

    /** Counts and totals per status and currency. */
    public List<StatusCount> statusCounts(UUID ownerId) {
        List<StatusCount> counts = new ArrayList<>();
        jdbc.query("""
                        SELECT status, currency, count(*) AS n, coalesce(sum(total), 0) AS total FROM quotes
                        WHERE user_id = :owner AND deleted_at IS NULL GROUP BY status, currency""",
                Map.of("owner", ownerId), (RowCallbackHandler) rs -> counts.add(new StatusCount(QuoteStatus.valueOf(rs.getString("status")),
                        rs.getString("currency"), rs.getLong("n"), rs.getBigDecimal("total"))));
        return List.copyOf(counts);
    }

    /** Totals of quotes accepted since {@code since}, per currency. */
    public Map<String, BigDecimal> acceptedSince(UUID ownerId, Instant since) {
        Map<String, BigDecimal> totals = new java.util.HashMap<>();
        jdbc.query("""
                        SELECT currency, coalesce(sum(total), 0) AS total FROM quotes
                        WHERE user_id = :owner AND deleted_at IS NULL AND status = 'ACCEPTED' AND accepted_at >= :since GROUP BY currency""",
                new MapSqlParameterSource("owner", ownerId).addValue("since", Timestamp.from(since)),
                (RowCallbackHandler) rs -> totals.put(rs.getString("currency"), rs.getBigDecimal("total")));
        return Map.copyOf(totals);
    }

    /** Decisions taken since {@code since} (accepted/rejected by decision time, expired by validity end). */
    public Decisions decisionsSince(UUID ownerId, Instant since, LocalDateTime sinceLocal) {
        return jdbc.queryForObject("""
                        SELECT count(*) FILTER (WHERE status = 'ACCEPTED' AND accepted_at >= :since) AS accepted,
                               count(*) FILTER (WHERE status = 'REJECTED' AND rejected_at >= :since) AS rejected,
                               count(*) FILTER (WHERE status = 'EXPIRED' AND valid_until >= :sinceLocal) AS expired
                        FROM quotes WHERE user_id = :owner AND deleted_at IS NULL""",
                new MapSqlParameterSource("owner", ownerId).addValue("since", Timestamp.from(since)).addValue("sinceLocal", sinceLocal),
                (rs, row) -> new Decisions(rs.getLong("accepted"), rs.getLong("rejected"), rs.getLong("expired")));
    }

    /** Open quotes of the owner whose validity ends before {@code until}. */
    public long expiringBefore(UUID ownerId, LocalDateTime now, LocalDateTime until) {
        Long count = jdbc.queryForObject("""
                        SELECT count(*) FROM quotes WHERE user_id = :owner AND deleted_at IS NULL AND status IN ('SENT', 'VIEWED')
                          AND valid_until >= :now AND valid_until < :until""",
                new MapSqlParameterSource("owner", ownerId).addValue("now", now).addValue("until", until), Long.class);
        return count == null ? 0 : count;
    }

    /** Open quotes (any owner) whose validity ended before {@code now}. */
    public List<UUID> overdueOpen(LocalDateTime now, int limit) {
        return jdbc.queryForList("""
                        SELECT id FROM quotes WHERE status IN ('SENT', 'VIEWED') AND deleted_at IS NULL AND valid_until < :now
                        ORDER BY valid_until LIMIT :limit""",
                new MapSqlParameterSource("now", now).addValue("limit", limit), UUID.class);
    }

    /** Open quotes (any owner) whose validity ends in [{@code now}, {@code until}). */
    public List<UUID> expiringOpen(LocalDateTime now, LocalDateTime until) {
        return jdbc.queryForList("""
                        SELECT id FROM quotes WHERE status IN ('SENT', 'VIEWED') AND deleted_at IS NULL
                          AND valid_until >= :now AND valid_until < :until""",
                new MapSqlParameterSource("now", now).addValue("until", until), UUID.class);
    }

    /** Clears the calendar link of quotes pointing at {@code eventId}. */
    public int unlinkCalendarEvent(UUID eventId) {
        return jdbc.update("UPDATE quotes SET calendar_event_id = NULL WHERE calendar_event_id = :id", Map.of("id", eventId));
    }

    /** Ids among {@code ids} that belong to {@code ownerId}. */
    public List<UUID> owned(UUID ownerId, Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.queryForList("SELECT id FROM quotes WHERE user_id = :owner AND id IN (:ids)",
                new MapSqlParameterSource("owner", ownerId).addValue("ids", ids), UUID.class);
    }
}
