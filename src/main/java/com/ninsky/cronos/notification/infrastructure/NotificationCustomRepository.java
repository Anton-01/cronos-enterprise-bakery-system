package com.ninsky.cronos.notification.infrastructure;

import com.ninsky.cronos.infrastructure.security.crypto.FieldEncryptionService;
import com.ninsky.cronos.notification.domain.NotificationCategory;
import com.ninsky.cronos.notification.domain.NotificationSeverity;
import com.ninsky.cronos.notification.domain.NotificationType;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** SQL of the {@code notifications} inbox (§6.1); title, body and actor are encrypted at rest. */
@Repository
public class NotificationCustomRepository {

    /** One notification, texts in clear. */
    public record NotificationRow(UUID id, UUID userId, NotificationType type, NotificationCategory category, NotificationSeverity severity,
                                  String title, String body, String link, String subjectType, UUID subjectId, String actorName,
                                  Instant createdAt, Instant readAt) {
    }

    static final Duration READ_RETENTION = Duration.ofDays(90);
    static final Duration RETENTION = Duration.ofDays(180);

    private final NamedParameterJdbcTemplate jdbc;
    private final FieldEncryptionService encryption;
    private final RowMapper<NotificationRow> rowMapper;

    public NotificationCustomRepository(NamedParameterJdbcTemplate jdbc, FieldEncryptionService encryption) {
        this.jdbc = jdbc;
        this.encryption = encryption;
        this.rowMapper = (rs, n) -> new NotificationRow(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                NotificationType.valueOf(rs.getString("type")), NotificationCategory.valueOf(rs.getString("category")),
                NotificationSeverity.valueOf(rs.getString("severity")), encryption.decrypt(rs.getString("title")),
                encryption.decrypt(rs.getString("body")), rs.getString("link"), rs.getString("subject_type"),
                rs.getObject("subject_id", UUID.class), encryption.decrypt(rs.getString("actor_name")),
                instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("read_at")));
    }

    /** Stores {@code row} once per (user, source event, type); false when it already exists (redelivery). */
    public boolean insert(NotificationRow row, UUID sourceEventId) {
        return jdbc.update("""
                        INSERT INTO notifications (id, user_id, type, category, severity, title, body, link, subject_type, subject_id,
                                                   actor_name, source_event_id, created_at)
                        VALUES (:id, :user, :type, :category, :severity, :title, :body, :link, :subjectType, :subjectId, :actor,
                                :source, :createdAt)
                        ON CONFLICT (user_id, source_event_id, type) DO NOTHING""",
                new MapSqlParameterSource("id", row.id()).addValue("user", row.userId()).addValue("type", row.type().name())
                        .addValue("category", row.category().name()).addValue("severity", row.severity().name())
                        .addValue("title", encryption.encrypt(row.title())).addValue("body", encryption.encrypt(row.body()))
                        .addValue("link", row.link()).addValue("subjectType", row.subjectType()).addValue("subjectId", row.subjectId())
                        .addValue("actor", encryption.encrypt(row.actorName())).addValue("source", sourceEventId)
                        .addValue("createdAt", Timestamp.from(row.createdAt()))) == 1;
    }

    /** Newest first. */
    public List<NotificationRow> page(UUID userId, boolean unreadOnly, Set<NotificationCategory> categories, int limit, long offset) {
        MapSqlParameterSource params = filter(userId, categories).addValue("limit", limit).addValue("offset", offset);
        return jdbc.query("SELECT * FROM notifications WHERE " + where(unreadOnly, categories) + " ORDER BY id DESC LIMIT :limit OFFSET :offset",
                params, rowMapper);
    }

    public long count(UUID userId, boolean unreadOnly, Set<NotificationCategory> categories) {
        Long total = jdbc.queryForObject("SELECT count(*) FROM notifications WHERE " + where(unreadOnly, categories),
                filter(userId, categories), Long.class);
        return total == null ? 0 : total;
    }

    /** Unread notifications per category (absent categories have none). */
    public Map<NotificationCategory, Long> unreadByCategory(UUID userId) {
        Map<NotificationCategory, Long> counts = new EnumMap<>(NotificationCategory.class);
        jdbc.query("SELECT category, count(*) AS unread FROM notifications WHERE user_id = :user AND read_at IS NULL GROUP BY category",
                Map.of("user", userId), rs -> {
                    counts.put(NotificationCategory.valueOf(rs.getString("category")), rs.getLong("unread"));
                });
        return counts;
    }

    /** Marks the caller's unread {@code ids} read; returns the ids that changed. */
    public List<UUID> markRead(UUID userId, Collection<UUID> ids, Instant now) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.queryForList("""
                        UPDATE notifications SET read_at = :now WHERE user_id = :user AND id IN (:ids) AND read_at IS NULL
                        RETURNING id""",
                new MapSqlParameterSource("user", userId).addValue("ids", Set.copyOf(ids)).addValue("now", Timestamp.from(now)), UUID.class);
    }

    public int markAllRead(UUID userId, Instant now) {
        return jdbc.update("UPDATE notifications SET read_at = :now WHERE user_id = :user AND read_at IS NULL",
                new MapSqlParameterSource("user", userId).addValue("now", Timestamp.from(now)));
    }

    public boolean delete(UUID userId, UUID id) {
        return jdbc.update("DELETE FROM notifications WHERE user_id = :user AND id = :id",
                new MapSqlParameterSource("user", userId).addValue("id", id)) == 1;
    }

    /** Notifications newer than {@code afterId} (UUIDv7 order), oldest first: the SSE replay after a reconnect. */
    public List<NotificationRow> after(UUID userId, UUID afterId, int limit) {
        return jdbc.query("SELECT * FROM notifications WHERE user_id = :user AND id > :after ORDER BY id LIMIT :limit",
                new MapSqlParameterSource("user", userId).addValue("after", afterId).addValue("limit", limit), rowMapper);
    }

    /** Retention (§6.1): read ones after 90 days, every one after 180 days. */
    public int purge(Instant now) {
        return jdbc.update("""
                        DELETE FROM notifications
                        WHERE created_at < :all OR (read_at IS NOT NULL AND created_at < :read)""",
                new MapSqlParameterSource("all", Timestamp.from(now.minus(RETENTION)))
                        .addValue("read", Timestamp.from(now.minus(READ_RETENTION))));
    }

    private static String where(boolean unreadOnly, Set<NotificationCategory> categories) {
        return "user_id = :user" + (unreadOnly ? " AND read_at IS NULL" : "") + (categories.isEmpty() ? "" : " AND category IN (:categories)");
    }

    private static MapSqlParameterSource filter(UUID userId, Set<NotificationCategory> categories) {
        MapSqlParameterSource params = new MapSqlParameterSource("user", userId);
        if (!categories.isEmpty()) {
            params.addValue("categories", categories.stream().map(Enum::name).toList());
        }
        return params;
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
