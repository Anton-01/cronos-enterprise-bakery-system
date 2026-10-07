package com.ninsky.cronos.kitchen.recipe.file;

import com.ninsky.cronos.finance.shared.UserRef;
import com.ninsky.cronos.finance.shared.UserRefMapper;
import com.ninsky.cronos.kitchen.shared.Sql;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** {@code recipe_files} persistence; blobs live in object storage under {@code storage_key}. */
@Repository
@RequiredArgsConstructor
public class RecipeFileCustomRepository {

    private static final String SELECT = "SELECT f.id, f.recipe_id, f.storage_key, f.file_name, f.kind, f.mime_type, f.size_bytes, "
            + "f.description, f.is_cover, f.thumbnail_key, f.uploaded_at, " + UserRefMapper.columns("f.uploaded_by")
            + " FROM recipe_files f" + UserRefMapper.join("f.uploaded_by");

    private final NamedParameterJdbcTemplate jdbc;
    private final UserRefMapper userRefs;

    public record Row(UUID id, UUID recipeId, String storageKey, String fileName, FileKind kind, String mimeType, long sizeBytes,
                      String description, boolean cover, String thumbnailKey, Instant uploadedAt, UserRef uploadedBy) {
    }

    public record NewFile(UUID id, UUID recipeId, String storageKey, String fileName, FileKind kind, String mimeType, long sizeBytes,
                          String sha256, String description, boolean cover, String thumbnailKey, UUID uploadedBy, Instant uploadedAt) {
    }

    public List<Row> list(UUID recipeId) {
        return jdbc.query(SELECT + " WHERE f.recipe_id = :recipe ORDER BY f.is_cover DESC, f.uploaded_at, f.id",
                Map.of("recipe", recipeId), (rs, i) -> row(rs));
    }

    public Optional<Row> find(UUID recipeId, UUID fileId) {
        return jdbc.query(SELECT + " WHERE f.recipe_id = :recipe AND f.id = :id",
                Map.of("recipe", recipeId, "id", fileId), (rs, i) -> row(rs)).stream().findFirst();
    }

    /** Cover object key per recipe (thumbnail preferred) for list rows. */
    public Map<UUID, String> coverKeys(Collection<UUID> recipeIds) {
        Map<UUID, String> keys = new HashMap<>();
        if (recipeIds.isEmpty()) {
            return keys;
        }
        jdbc.query("SELECT recipe_id, coalesce(thumbnail_key, storage_key) AS k FROM recipe_files WHERE is_cover AND recipe_id IN (:ids)",
                Map.of("ids", Set.copyOf(recipeIds)), rs -> {
                    keys.put(rs.getObject("recipe_id", UUID.class), rs.getString("k"));
                });
        return keys;
    }

    public int count(UUID recipeId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM recipe_files WHERE recipe_id = :recipe", Map.of("recipe", recipeId), Integer.class);
        return count == null ? 0 : count;
    }

    public boolean hasCover(UUID recipeId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM recipe_files WHERE recipe_id = :recipe AND is_cover)",
                Map.of("recipe", recipeId), Boolean.class));
    }

    /** Bytes stored by the tenant across its recipes (deleted recipes count until purged). */
    public long tenantBytes(UUID tenant) {
        Long bytes = jdbc.queryForObject("""
                SELECT coalesce(sum(f.size_bytes), 0) FROM recipe_files f JOIN recipes r ON r.id = f.recipe_id WHERE r.owner_id = :tenant""",
                Map.of("tenant", tenant), Long.class);
        return bytes == null ? 0 : bytes;
    }

    public void insert(NewFile file) {
        jdbc.update("""
                INSERT INTO recipe_files (id, recipe_id, storage_key, file_name, kind, mime_type, size_bytes, sha256, description, is_cover,
                    thumbnail_key, uploaded_at, uploaded_by)
                VALUES (:id, :recipe, :key, :name, :kind, :mime, :size, :sha, :description, :cover, :thumb, :at, :by)""",
                new MapSqlParameterSource().addValue("id", file.id()).addValue("recipe", file.recipeId()).addValue("key", file.storageKey())
                        .addValue("name", file.fileName()).addValue("kind", file.kind().name()).addValue("mime", file.mimeType())
                        .addValue("size", file.sizeBytes()).addValue("sha", file.sha256()).addValue("description", file.description())
                        .addValue("cover", file.cover()).addValue("thumb", file.thumbnailKey())
                        .addValue("at", file.uploadedAt().atOffset(ZoneOffset.UTC)).addValue("by", file.uploadedBy()));
    }

    public void updateDescription(UUID fileId, String description) {
        jdbc.update("UPDATE recipe_files SET description = :description WHERE id = :id",
                new MapSqlParameterSource().addValue("id", fileId).addValue("description", description));
    }

    /** Makes {@code fileId} the only cover of the recipe (unset first: the unique index allows one). */
    public void makeCover(UUID recipeId, UUID fileId) {
        jdbc.update("UPDATE recipe_files SET is_cover = FALSE WHERE recipe_id = :recipe AND is_cover AND id <> :id",
                Map.of("recipe", recipeId, "id", fileId));
        jdbc.update("UPDATE recipe_files SET is_cover = TRUE WHERE id = :id", Map.of("id", fileId));
    }

    public void unsetCover(UUID fileId) {
        jdbc.update("UPDATE recipe_files SET is_cover = FALSE WHERE id = :id", Map.of("id", fileId));
    }

    /** Oldest remaining image becomes cover, if any. */
    public void promoteCover(UUID recipeId) {
        jdbc.update("""
                UPDATE recipe_files SET is_cover = TRUE WHERE id = (SELECT id FROM recipe_files WHERE recipe_id = :recipe AND kind = 'IMAGE'
                    ORDER BY uploaded_at, id LIMIT 1) AND NOT EXISTS (SELECT 1 FROM recipe_files WHERE recipe_id = :recipe AND is_cover)""",
                Map.of("recipe", recipeId));
    }

    public void delete(UUID fileId) {
        jdbc.update("DELETE FROM recipe_files WHERE id = :id", Map.of("id", fileId));
    }

    private Row row(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Row(rs.getObject("id", UUID.class), rs.getObject("recipe_id", UUID.class), rs.getString("storage_key"),
                rs.getString("file_name"), FileKind.valueOf(rs.getString("kind")), rs.getString("mime_type"), rs.getLong("size_bytes"),
                rs.getString("description"), rs.getBoolean("is_cover"), rs.getString("thumbnail_key"), Sql.instant(rs, "uploaded_at"),
                userRefs.map(rs));
    }
}
