package com.ninsky.cronos.kitchen.section;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * {@code recipe_sections}. The section key is always computed by the database ({@code kitchen_section_key}) so
 * labels and recipe lines are compared with one definition.
 */
@Repository
@RequiredArgsConstructor
public class RecipeSectionCustomRepository {

    /** Live lines of the owner's recipes per section key: one grouped query feeds every usageCount. */
    private static final String SELECT = """
            SELECT s.id, s.name, s.color, s.display_order, coalesce(u.n, 0) AS usage_count
            FROM recipe_sections s
            LEFT JOIN (SELECT kitchen_section_key(l.section) AS k, count(*) AS n
                       FROM recipe_lines l JOIN recipes r ON r.id = l.recipe_id
                       WHERE r.owner_id = :owner AND r.deleted_at IS NULL AND l.section IS NOT NULL
                       GROUP BY 1) u ON u.k = s.name_key
            WHERE s.owner_id = :owner""";

    private final NamedParameterJdbcTemplate jdbc;

    public List<RecipeSection> list(UUID owner) {
        return jdbc.query(SELECT + " ORDER BY s.display_order, s.name, s.id", Map.of("owner", owner),
                (rs, i) -> new RecipeSection(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("color"),
                        rs.getInt("display_order"), rs.getInt("usage_count")));
    }

    public Optional<RecipeSection> find(UUID owner, UUID id) {
        return jdbc.query(SELECT + " AND s.id = :id", new MapSqlParameterSource().addValue("owner", owner).addValue("id", id),
                (rs, i) -> new RecipeSection(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("color"),
                        rs.getInt("display_order"), rs.getInt("usage_count"))).stream().findFirst();
    }

    /** Ids in display order (for reordering). */
    public List<UUID> orderedIds(UUID owner) {
        return jdbc.queryForList("SELECT id FROM recipe_sections WHERE owner_id = :owner ORDER BY display_order, name, id",
                Map.of("owner", owner), UUID.class);
    }

    public int count(UUID owner) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM recipe_sections WHERE owner_id = :owner", Map.of("owner", owner), Integer.class);
        return count == null ? 0 : count;
    }

    /** Another label of the owner with the same section key. */
    public boolean keyTaken(UUID owner, String name, UUID excludeId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM recipe_sections WHERE owner_id = :owner AND name_key = kitchen_section_key(:name)
                    AND id IS DISTINCT FROM CAST(:exclude AS uuid))""",
                new MapSqlParameterSource().addValue("owner", owner).addValue("name", name).addValue("exclude", excludeId), Boolean.class));
    }

    /** Appends a label ({@code display_order = max + 1}). */
    public void insert(UUID owner, UUID id, String name, String color, Instant now) {
        jdbc.update("""
                INSERT INTO recipe_sections (id, owner_id, name, name_key, color, display_order, created_at, updated_at)
                VALUES (:id, :owner, :name, kitchen_section_key(:name), :color,
                        (SELECT coalesce(max(display_order) + 1, 0) FROM recipe_sections WHERE owner_id = :owner), :now, :now)""",
                new MapSqlParameterSource().addValue("id", id).addValue("owner", owner).addValue("name", name).addValue("color", color)
                        .addValue("now", now.atOffset(ZoneOffset.UTC)));
    }

    public boolean update(UUID owner, UUID id, String name, String color, Instant now) {
        return jdbc.update("""
                UPDATE recipe_sections SET name = :name, name_key = kitchen_section_key(:name), color = :color, updated_at = :now
                WHERE owner_id = :owner AND id = :id""",
                new MapSqlParameterSource().addValue("id", id).addValue("owner", owner).addValue("name", name).addValue("color", color)
                        .addValue("now", now.atOffset(ZoneOffset.UTC))) == 1;
    }

    public boolean delete(UUID owner, UUID id) {
        return jdbc.update("DELETE FROM recipe_sections WHERE owner_id = :owner AND id = :id", Map.of("owner", owner, "id", id)) == 1;
    }

    /** {@code ids[i]} gets {@code display_order = i}. */
    public void reorder(UUID owner, List<UUID> ids, Instant now) {
        jdbc.batchUpdate("UPDATE recipe_sections SET display_order = :order, updated_at = :now WHERE owner_id = :owner AND id = :id",
                IntStream.range(0, ids.size()).mapToObj(i -> new MapSqlParameterSource().addValue("owner", owner).addValue("id", ids.get(i))
                        .addValue("order", i).addValue("now", now.atOffset(ZoneOffset.UTC))).toArray(SqlParameterSource[]::new));
    }

    /**
     * Defaults whose key the owner lacks, appended in catalog order, at most {@code room}; never renames or deletes.
     * Returns rows inserted.
     */
    public int insertMissingDefaults(UUID owner, List<RecipeSectionDefaults.Seed> seeds, int room, Instant now) {
        int inserted = 0;
        for (RecipeSectionDefaults.Seed seed : seeds) {
            if (inserted >= room) {
                break;
            }
            inserted += jdbc.update("""
                    INSERT INTO recipe_sections (id, owner_id, name, name_key, display_order, seed_code, created_at, updated_at)
                    SELECT :id, :owner, :name, kitchen_section_key(:name),
                           (SELECT coalesce(max(display_order) + 1, 0) FROM recipe_sections WHERE owner_id = :owner), :code, :now, :now
                    ON CONFLICT (owner_id, name_key) DO NOTHING""",
                    new MapSqlParameterSource().addValue("id", UUID.randomUUID()).addValue("owner", owner).addValue("name", seed.name())
                            .addValue("code", seed.code()).addValue("now", now.atOffset(ZoneOffset.UTC)));
        }
        return inserted;
    }

    /**
     * Every distinct section already written in the owner's live recipes that has no label yet (so existing data shows
     * up in the catalog), up to {@code room} labels, alphabetically after the current ones.
     */
    public int insertUsedSections(UUID owner, int room, Instant now) {
        if (room <= 0) {
            return 0;
        }
        return jdbc.update("""
                INSERT INTO recipe_sections (id, owner_id, name, name_key, display_order, created_at, updated_at)
                SELECT gen_random_uuid(), :owner, used.name, used.k,
                       (SELECT coalesce(max(display_order), -1) FROM recipe_sections WHERE owner_id = :owner) + row_number() OVER (ORDER BY used.name),
                       :now, :now
                FROM (SELECT kitchen_section_key(l.section) AS k, min(regexp_replace(btrim(l.section), '\\s+', ' ', 'g')) AS name
                      FROM recipe_lines l JOIN recipes r ON r.id = l.recipe_id
                      WHERE r.owner_id = :owner AND r.deleted_at IS NULL AND l.section IS NOT NULL AND btrim(l.section) <> ''
                      GROUP BY 1) used
                WHERE NOT EXISTS (SELECT 1 FROM recipe_sections s WHERE s.owner_id = :owner AND s.name_key = used.k)
                ORDER BY used.name
                LIMIT :room
                ON CONFLICT (owner_id, name_key) DO NOTHING""",
                new MapSqlParameterSource().addValue("owner", owner).addValue("room", room).addValue("now", now.atOffset(ZoneOffset.UTC)));
    }
}
