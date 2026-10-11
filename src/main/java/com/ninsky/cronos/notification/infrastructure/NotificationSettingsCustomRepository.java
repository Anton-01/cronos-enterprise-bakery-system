package com.ninsky.cronos.notification.infrastructure;

import com.ninsky.cronos.notification.domain.NotificationCategory;
import com.ninsky.cronos.notification.domain.NotificationChannel;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Time;
import java.time.LocalTime;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** SQL of {@code notification_settings} and {@code notification_preferences} (§6.5). */
@Repository
public class NotificationSettingsCustomRepository {

    /** The scalar settings of one user. */
    public record SettingsRow(UUID userId, boolean quietEnabled, LocalTime quietStart, LocalTime quietEnd, int defaultReminderMinutes,
                              String timezone, long version) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public NotificationSettingsCustomRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Seeds the defaults row (a no-op when it exists). */
    public void seed(UUID userId) {
        jdbc.update("INSERT INTO notification_settings (user_id) VALUES (:user) ON CONFLICT (user_id) DO NOTHING", Map.of("user", userId));
    }

    public Optional<SettingsRow> find(UUID userId) {
        return settings(List.of(userId)).values().stream().findFirst();
    }

    /** Settings rows of {@code userIds} (users without a row are absent). */
    public Map<UUID, SettingsRow> settings(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, SettingsRow> rows = new HashMap<>();
        jdbc.query("SELECT * FROM notification_settings WHERE user_id IN (:users)", Map.of("users", Set.copyOf(userIds)), rs -> {
            UUID user = rs.getObject("user_id", UUID.class);
            rows.put(user, new SettingsRow(user, rs.getBoolean("quiet_enabled"), time(rs.getTime("quiet_start")), time(rs.getTime("quiet_end")),
                    rs.getInt("default_reminder_minutes"), rs.getString("timezone"), rs.getLong("version")));
        });
        return rows;
    }

    /** Stored channel choices per user and category (categories never written are absent). */
    public Map<UUID, Map<NotificationCategory, Set<NotificationChannel>>> preferences(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Map<NotificationCategory, Set<NotificationChannel>>> result = new HashMap<>();
        jdbc.query("SELECT * FROM notification_preferences WHERE user_id IN (:users)", Map.of("users", Set.copyOf(userIds)), rs -> {
            EnumSet<NotificationChannel> channels = EnumSet.noneOf(NotificationChannel.class);
            if (rs.getBoolean("in_app")) {
                channels.add(NotificationChannel.IN_APP);
            }
            if (rs.getBoolean("push")) {
                channels.add(NotificationChannel.PUSH);
            }
            if (rs.getBoolean("email")) {
                channels.add(NotificationChannel.EMAIL);
            }
            result.computeIfAbsent(rs.getObject("user_id", UUID.class), user -> new EnumMap<>(NotificationCategory.class))
                    .put(NotificationCategory.valueOf(rs.getString("category")), channels);
        });
        return result;
    }

    /**
     * Writes the settings when the stored version is {@code expectedVersion}.
     *
     * @return false on a stale version
     */
    public boolean update(SettingsRow row, long expectedVersion) {
        return jdbc.update("""
                        UPDATE notification_settings SET quiet_enabled = :quietEnabled, quiet_start = :start, quiet_end = :end,
                               default_reminder_minutes = :reminder, timezone = :timezone, version = version + 1
                        WHERE user_id = :user AND version = :expected""",
                new MapSqlParameterSource("user", row.userId()).addValue("quietEnabled", row.quietEnabled())
                        .addValue("start", row.quietStart() == null ? null : Time.valueOf(row.quietStart()))
                        .addValue("end", row.quietEnd() == null ? null : Time.valueOf(row.quietEnd()))
                        .addValue("reminder", row.defaultReminderMinutes()).addValue("timezone", row.timezone())
                        .addValue("expected", expectedVersion)) == 1;
    }

    /** Replaces every category row of the user. */
    public void replacePreferences(UUID userId, Map<NotificationCategory, Set<NotificationChannel>> preferences) {
        jdbc.update("DELETE FROM notification_preferences WHERE user_id = :user", Map.of("user", userId));
        MapSqlParameterSource[] batch = preferences.entrySet().stream().map(entry -> new MapSqlParameterSource("user", userId)
                .addValue("category", entry.getKey().name())
                .addValue("inApp", entry.getValue().contains(NotificationChannel.IN_APP))
                .addValue("push", entry.getValue().contains(NotificationChannel.PUSH))
                .addValue("email", entry.getValue().contains(NotificationChannel.EMAIL)))
                .toArray(MapSqlParameterSource[]::new);
        jdbc.batchUpdate("""
                INSERT INTO notification_preferences (user_id, category, in_app, push, email)
                VALUES (:user, :category, :inApp, :push, :email)""", batch);
    }

    private static LocalTime time(Time time) {
        return time == null ? null : time.toLocalTime();
    }
}
