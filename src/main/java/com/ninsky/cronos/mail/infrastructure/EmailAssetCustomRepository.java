package com.ninsky.cronos.mail.infrastructure;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Uploaded email images ({@code email_asset}), content-addressed by SHA-256. Assets a saved override or the settings
 * point to are flagged {@code referenced} and never deleted (old emails in inboxes still load them).
 */
@Repository
public class EmailAssetCustomRepository {

    /** One stored asset. */
    public record AssetRow(UUID id, String sha256, String storageKey, String contentType, int sizeBytes, Integer width, Integer height) {
    }

    private static final RowMapper<AssetRow> ROW = (rs, n) -> new AssetRow(rs.getObject("id", UUID.class), rs.getString("sha256"),
            rs.getString("storage_key"), rs.getString("content_type"), rs.getInt("size_bytes"), (Integer) rs.getObject("width"),
            (Integer) rs.getObject("height"));

    private final NamedParameterJdbcTemplate jdbc;

    public EmailAssetCustomRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<AssetRow> findBySha(UUID tenant, String sha256) {
        return jdbc.query("SELECT * FROM email_asset WHERE tenant_id = :tenant AND sha256 = :sha", Map.of("tenant", tenant, "sha", sha256), ROW)
                .stream().findFirst();
    }

    /** Stores the row; false when the same content was stored concurrently (the caller re-reads it). */
    public boolean insert(UUID tenant, AssetRow row, UUID actor, Instant now) {
        return jdbc.update("""
                INSERT INTO email_asset (id, tenant_id, sha256, storage_key, content_type, size_bytes, width, height, created_at, created_by)
                VALUES (:id, :tenant, :sha, :key, :type, :size, :width, :height, :now, :actor)
                ON CONFLICT (tenant_id, sha256) DO NOTHING""",
                new MapSqlParameterSource().addValue("id", row.id()).addValue("tenant", tenant).addValue("sha", row.sha256())
                        .addValue("key", row.storageKey()).addValue("type", row.contentType()).addValue("size", row.sizeBytes())
                        .addValue("width", row.width()).addValue("height", row.height()).addValue("now", Timestamp.from(now))
                        .addValue("actor", actor)) == 1;
    }

    /** Flags the assets stored under {@code storageKeys} as referenced (idempotent). */
    public void markReferenced(UUID tenant, Collection<String> storageKeys) {
        if (storageKeys.isEmpty()) {
            return;
        }
        jdbc.update("UPDATE email_asset SET referenced = TRUE WHERE tenant_id = :tenant AND storage_key IN (:keys) AND NOT referenced",
                new MapSqlParameterSource().addValue("tenant", tenant).addValue("keys", List.copyOf(storageKeys)));
    }

    /** Never-referenced assets created before {@code cutoff} (cleanup candidates). */
    public List<AssetRow> unreferencedBefore(Instant cutoff, int limit) {
        return jdbc.query("SELECT * FROM email_asset WHERE NOT referenced AND created_at < :cutoff ORDER BY created_at LIMIT :limit",
                new MapSqlParameterSource().addValue("cutoff", Timestamp.from(cutoff)).addValue("limit", limit), ROW);
    }

    /** Deletes the row unless it became referenced meanwhile; true when deleted. */
    public boolean deleteUnreferenced(UUID id) {
        return jdbc.update("DELETE FROM email_asset WHERE id = :id AND NOT referenced", Map.of("id", id)) == 1;
    }
}
