package com.ninsky.cronos.notification.infrastructure;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** SQL of {@code push_subscriptions} and {@code push_deliveries} (§6.4). */
@Repository
public class PushSubscriptionCustomRepository {

    /** A browser subscription of a user. */
    public record SubscriptionRow(UUID id, UUID userId, String endpoint, String p256dh, String auth, String deviceLabel, String browser,
                                  String os, String userAgent, String locale, Instant createdAt, Instant lastSuccessAt, int failureCount,
                                  Instant revokedAt) {
    }

    /** Outcome of one push attempt to one subscription. */
    public enum DeliveryStatus { DELIVERED, RETRYING, FAILED, REVOKED }

    static final RowMapper<SubscriptionRow> ROW = (rs, n) -> new SubscriptionRow(rs.getObject("id", UUID.class),
            rs.getObject("user_id", UUID.class), rs.getString("endpoint"), rs.getString("p256dh"), rs.getString("auth"),
            rs.getString("device_label"), rs.getString("browser"), rs.getString("os"), rs.getString("user_agent"), rs.getString("locale"),
            instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("last_success_at")), rs.getInt("failure_count"),
            instant(rs.getTimestamp("revoked_at")));

    private final NamedParameterJdbcTemplate jdbc;

    public PushSubscriptionCustomRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Inserts or refreshes the subscription of {@code endpoint}; an endpoint already known moves to {@code row.userId()}
     * (the browser profile changed hands) and becomes active again.
     *
     * @return the stored id
     */
    public UUID upsert(SubscriptionRow row, String endpointHash) {
        return jdbc.queryForObject("""
                        INSERT INTO push_subscriptions (id, user_id, endpoint, endpoint_hash, p256dh, auth, device_label, browser, os,
                                                        user_agent, locale, created_at)
                        VALUES (:id, :user, :endpoint, :hash, :p256dh, :auth, :label, :browser, :os, :ua, :locale, :now)
                        ON CONFLICT (endpoint) DO UPDATE SET user_id = EXCLUDED.user_id, p256dh = EXCLUDED.p256dh, auth = EXCLUDED.auth,
                               device_label = coalesce(EXCLUDED.device_label, push_subscriptions.device_label), browser = EXCLUDED.browser,
                               os = EXCLUDED.os, user_agent = EXCLUDED.user_agent, locale = EXCLUDED.locale, failure_count = 0,
                               revoked_at = NULL
                        RETURNING id""",
                new MapSqlParameterSource("id", row.id()).addValue("user", row.userId()).addValue("endpoint", row.endpoint())
                        .addValue("hash", endpointHash).addValue("p256dh", row.p256dh()).addValue("auth", row.auth())
                        .addValue("label", row.deviceLabel()).addValue("browser", row.browser()).addValue("os", row.os())
                        .addValue("ua", row.userAgent()).addValue("locale", row.locale()).addValue("now", Timestamp.from(row.createdAt())),
                UUID.class);
    }

    /** Active subscriptions of a user, most recently successful first. */
    public List<SubscriptionRow> active(UUID userId) {
        return jdbc.query("""
                        SELECT * FROM push_subscriptions WHERE user_id = :user AND revoked_at IS NULL
                        ORDER BY last_success_at DESC NULLS LAST, created_at DESC""",
                Map.of("user", userId), ROW);
    }

    public Optional<SubscriptionRow> findActive(UUID userId, UUID id) {
        return jdbc.query("SELECT * FROM push_subscriptions WHERE id = :id AND user_id = :user AND revoked_at IS NULL",
                new MapSqlParameterSource("id", id).addValue("user", userId), ROW).stream().findFirst();
    }

    public Optional<SubscriptionRow> findActiveByEndpoint(String endpoint) {
        return jdbc.query("SELECT * FROM push_subscriptions WHERE endpoint = :endpoint AND revoked_at IS NULL", Map.of("endpoint", endpoint), ROW)
                .stream().findFirst();
    }

    public boolean revoke(UUID id, Instant now) {
        return jdbc.update("UPDATE push_subscriptions SET revoked_at = :now WHERE id = :id AND revoked_at IS NULL",
                new MapSqlParameterSource("id", id).addValue("now", Timestamp.from(now))) == 1;
    }

    public boolean revokeByEndpoint(UUID userId, String endpoint, Instant now) {
        return jdbc.update("UPDATE push_subscriptions SET revoked_at = :now WHERE user_id = :user AND endpoint = :endpoint AND revoked_at IS NULL",
                new MapSqlParameterSource("user", userId).addValue("endpoint", endpoint).addValue("now", Timestamp.from(now))) == 1;
    }

    /** Keeps the {@code max} most recently used active subscriptions of a user, revoking the rest. */
    public int revokeBeyond(UUID userId, int max, Instant now) {
        return jdbc.update("""
                        UPDATE push_subscriptions SET revoked_at = :now
                        WHERE id IN (SELECT id FROM push_subscriptions WHERE user_id = :user AND revoked_at IS NULL
                                     ORDER BY last_success_at DESC NULLS LAST, created_at DESC OFFSET :max)""",
                new MapSqlParameterSource("user", userId).addValue("max", max).addValue("now", Timestamp.from(now)));
    }

    /** Moves an active subscription to its renewed endpoint and keys (service-worker renewal). */
    public void renew(UUID id, String endpoint, String endpointHash, String p256dh, String auth) {
        jdbc.update("""
                        UPDATE push_subscriptions SET endpoint = :endpoint, endpoint_hash = :hash, p256dh = :p256dh, auth = :auth,
                               failure_count = 0
                        WHERE id = :id""",
                new MapSqlParameterSource("id", id).addValue("endpoint", endpoint).addValue("hash", endpointHash)
                        .addValue("p256dh", p256dh).addValue("auth", auth));
    }

    public void succeeded(UUID id, Instant now) {
        jdbc.update("UPDATE push_subscriptions SET last_success_at = :now, failure_count = 0 WHERE id = :id",
                new MapSqlParameterSource("id", id).addValue("now", Timestamp.from(now)));
    }

    /** Counts a failed attempt; returns the consecutive failures. */
    public int failed(UUID id) {
        Integer count = jdbc.queryForObject("UPDATE push_subscriptions SET failure_count = failure_count + 1 WHERE id = :id RETURNING failure_count",
                Map.of("id", id), Integer.class);
        return count == null ? 0 : count;
    }

    // ─── deliveries ────────────────────────────────────────────────────────────────────

    /** Subscriptions already done with {@code eventId} (delivered or failed for good): a retry skips them. */
    public Set<UUID> settled(UUID eventId) {
        return Set.copyOf(jdbc.queryForList("""
                        SELECT subscription_id FROM push_deliveries WHERE event_id = :event AND status IN ('DELIVERED', 'FAILED', 'REVOKED')""",
                Map.of("event", eventId), UUID.class));
    }

    /** Records an attempt (one row per event and subscription, attempts counted). */
    public void recordDelivery(UUID id, UUID eventId, UUID subscriptionId, UUID notificationId, DeliveryStatus status, Integer httpStatus,
                               String error, Instant now, String traceId) {
        jdbc.update("""
                        INSERT INTO push_deliveries (id, subscription_id, notification_id, event_id, status, http_status, attempts, last_error,
                                                     created_at, delivered_at, trace_id)
                        VALUES (:id, :subscription, :notification, :event, :status, :http, 1, :error, :now,
                                CASE WHEN :status = 'DELIVERED' THEN CAST(:now AS TIMESTAMPTZ) END, :trace)
                        ON CONFLICT (event_id, subscription_id) DO UPDATE SET status = EXCLUDED.status, http_status = EXCLUDED.http_status,
                               attempts = push_deliveries.attempts + 1, last_error = EXCLUDED.last_error,
                               delivered_at = EXCLUDED.delivered_at""",
                new MapSqlParameterSource("id", id).addValue("subscription", subscriptionId).addValue("notification", notificationId)
                        .addValue("event", eventId).addValue("status", status.name()).addValue("http", httpStatus)
                        .addValue("error", error == null || error.length() <= 500 ? error : error.substring(0, 500))
                        .addValue("now", Timestamp.from(now)).addValue("trace", traceId));
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
