package com.ninsky.cronos.kitchen.guide;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ninsky.cronos.kitchen.shared.Sql;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Persistence of the baker's guide ({@code guide_*}); JSON columns hold structured blocks, never HTML. */
@Repository
@RequiredArgsConstructor
public class GuideCustomRepository {

    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {
    };
    private static final TypeReference<List<GuideBlock>> BLOCKS = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, GuideAdmin.ArticleTranslation>> ARTICLE_TRANSLATIONS = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, GuideAdmin.PanTranslation>> PAN_TRANSLATIONS = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, GuideAdmin.ConversionTranslation>> CONVERSION_TRANSLATIONS = new TypeReference<>() {
    };

    private static final String ARTICLE = """
            SELECT id, code, category, title, summary, icon, tags, blocks, sources, translations, display_order, is_active, updated_at
            FROM guide_articles""";
    private static final String PAN = """
            SELECT id, code, owner_id, shape, name, diameter_cm, length_cm, width_cm, height_cm, volume_ml, servings, notes, translations,
                   display_order, updated_at
            FROM guide_pan_sizes""";
    private static final String CONVERSION = """
            SELECT code, name, grams_per_cup, grams_per_tablespoon, grams_per_teaspoon, translations, display_order, updated_at
            FROM guide_conversions""";

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public record ArticleRow(UUID id, String code, GuideCategory category, String title, String summary, String icon, List<String> tags,
                             List<GuideBlock> blocks, List<String> sources, Map<String, GuideAdmin.ArticleTranslation> translations,
                             int displayOrder, boolean active, Instant updatedAt) {
    }

    /** {@code ownerId} null = SYSTEM. */
    public record PanRow(UUID id, String code, UUID ownerId, PanSizeRequest size, Map<String, GuideAdmin.PanTranslation> translations,
                         int displayOrder, Instant updatedAt) {

        public GuideScope scope() {
            return ownerId == null ? GuideScope.SYSTEM : GuideScope.USER;
        }
    }

    public record ConversionRow(IngredientConversion conversion, Map<String, GuideAdmin.ConversionTranslation> translations, int displayOrder,
                                Instant updatedAt) {
    }

    // ─── reads ──────────────────────────────────────────────────────────────────────────

    /**
     * Changes whenever anything the caller's guide shows changes: revision, last update and row count of the SYSTEM
     * content and of the caller's pans (counts catch deletions). One cheap query, so 304s skip loading the content.
     */
    public String fingerprint(UUID owner) {
        return jdbc.queryForObject("""
                SELECT concat_ws('|', (SELECT revision::text FROM guide_meta WHERE id = 1),
                       (SELECT count(*) || '@' || coalesce(max(updated_at)::text, '') FROM guide_articles),
                       (SELECT count(*) || '@' || coalesce(max(updated_at)::text, '') FROM guide_pan_sizes WHERE owner_id IS NULL),
                       (SELECT count(*) || '@' || coalesce(max(updated_at)::text, '') FROM guide_conversions),
                       (SELECT count(*) || '@' || coalesce(max(updated_at)::text, '') FROM guide_pan_sizes WHERE owner_id = :owner))""",
                new MapSqlParameterSource().addValue("owner", owner), String.class);
    }

    public Optional<LocalDate> revision() {
        return jdbc.queryForList("SELECT revision FROM guide_meta WHERE id = 1", Map.of(), LocalDate.class).stream().findFirst();
    }

    public List<ArticleRow> articles(boolean activeOnly) {
        return jdbc.query(ARTICLE + (activeOnly ? " WHERE is_active" : "")
                + " ORDER BY array_position(ARRAY['FOOD_SAFETY', 'TECHNIQUES', 'COSTING']::varchar[], category), display_order, code", Map.of(),
                (rs, i) -> article(rs));
    }

    public Optional<ArticleRow> article(UUID id) {
        return jdbc.query(ARTICLE + " WHERE id = :id", Map.of("id", id), (rs, i) -> article(rs)).stream().findFirst();
    }

    /** SYSTEM pans by {@code display_order}, then the owner's by name. */
    public List<PanRow> pans(UUID owner) {
        return jdbc.query(PAN + """
                 WHERE owner_id IS NULL OR owner_id = :owner
                ORDER BY owner_id IS NOT NULL, CASE WHEN owner_id IS NULL THEN display_order END, lower(name), id""",
                new MapSqlParameterSource().addValue("owner", owner), (rs, i) -> pan(rs));
    }

    public List<PanRow> systemPans() {
        return jdbc.query(PAN + " WHERE owner_id IS NULL ORDER BY display_order, code", Map.of(), (rs, i) -> pan(rs));
    }

    public Optional<PanRow> pan(UUID id) {
        return jdbc.query(PAN + " WHERE id = :id", Map.of("id", id), (rs, i) -> pan(rs)).stream().findFirst();
    }

    public int userPanCount(UUID owner) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM guide_pan_sizes WHERE owner_id = :owner", Map.of("owner", owner), Integer.class);
        return count == null ? 0 : count;
    }

    public boolean userPanNameTaken(UUID owner, String name, UUID excludeId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM guide_pan_sizes WHERE owner_id = :owner AND lower(name) = lower(:name)
                    AND id IS DISTINCT FROM CAST(:exclude AS uuid))""",
                new MapSqlParameterSource().addValue("owner", owner).addValue("name", name).addValue("exclude", excludeId), Boolean.class));
    }

    public boolean panCodeTaken(String code, UUID excludeId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM guide_pan_sizes WHERE code = :code AND id IS DISTINCT FROM CAST(:exclude AS uuid))",
                new MapSqlParameterSource().addValue("code", code).addValue("exclude", excludeId), Boolean.class));
    }

    public boolean articleCodeTaken(String code, UUID excludeId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM guide_articles WHERE code = :code AND id IS DISTINCT FROM CAST(:exclude AS uuid))",
                new MapSqlParameterSource().addValue("code", code).addValue("exclude", excludeId), Boolean.class));
    }

    public List<ConversionRow> conversions() {
        return jdbc.query(CONVERSION + " ORDER BY display_order, code", Map.of(), (rs, i) -> conversion(rs));
    }

    public Optional<ConversionRow> conversion(String code) {
        return jdbc.query(CONVERSION + " WHERE code = :code", Map.of("code", code), (rs, i) -> conversion(rs)).stream().findFirst();
    }

    // ─── writes ─────────────────────────────────────────────────────────────────────────

    /** Insert or update by {@code code} (the seed's stable ids are kept on insert). */
    public void upsertArticle(ArticleRow row) {
        jdbc.update("""
                INSERT INTO guide_articles (id, code, category, title, summary, icon, tags, blocks, sources, translations, display_order,
                    is_active, updated_at)
                VALUES (:id, :code, :category, :title, :summary, :icon, CAST(:tags AS jsonb), CAST(:blocks AS jsonb), CAST(:sources AS jsonb),
                    CAST(:translations AS jsonb), :order, :active, now())
                ON CONFLICT (code) DO UPDATE SET category = EXCLUDED.category, title = EXCLUDED.title, summary = EXCLUDED.summary,
                    icon = EXCLUDED.icon, tags = EXCLUDED.tags, blocks = EXCLUDED.blocks, sources = EXCLUDED.sources,
                    translations = EXCLUDED.translations, display_order = EXCLUDED.display_order, is_active = EXCLUDED.is_active,
                    updated_at = now()""",
                articleParams(row));
    }

    public boolean updateArticle(ArticleRow row) {
        return jdbc.update("""
                UPDATE guide_articles SET code = :code, category = :category, title = :title, summary = :summary, icon = :icon,
                    tags = CAST(:tags AS jsonb), blocks = CAST(:blocks AS jsonb), sources = CAST(:sources AS jsonb),
                    translations = CAST(:translations AS jsonb), display_order = :order, is_active = :active, updated_at = now()
                WHERE id = :id""", articleParams(row)) == 1;
    }

    public boolean deleteArticle(UUID id) {
        return jdbc.update("DELETE FROM guide_articles WHERE id = :id", Map.of("id", id)) == 1;
    }

    /** Insert or update by {@code code}; SYSTEM rows have {@code ownerId} null. */
    public void upsertPan(PanRow row) {
        jdbc.update("""
                INSERT INTO guide_pan_sizes (id, code, owner_id, shape, name, diameter_cm, length_cm, width_cm, height_cm, volume_ml,
                    servings, notes, translations, display_order, updated_at)
                VALUES (:id, :code, :owner, :shape, :name, :diameter, :length, :width, :height, :volume, :servings, :notes,
                    CAST(:translations AS jsonb), :order, now())
                ON CONFLICT (code) DO UPDATE SET shape = EXCLUDED.shape, name = EXCLUDED.name, diameter_cm = EXCLUDED.diameter_cm,
                    length_cm = EXCLUDED.length_cm, width_cm = EXCLUDED.width_cm, height_cm = EXCLUDED.height_cm,
                    volume_ml = EXCLUDED.volume_ml, servings = EXCLUDED.servings, notes = EXCLUDED.notes,
                    translations = EXCLUDED.translations, display_order = EXCLUDED.display_order, updated_at = now()""",
                panParams(row));
    }

    public boolean updatePan(PanRow row) {
        return jdbc.update("""
                UPDATE guide_pan_sizes SET code = :code, shape = :shape, name = :name, diameter_cm = :diameter, length_cm = :length,
                    width_cm = :width, height_cm = :height, volume_ml = :volume, servings = :servings, notes = :notes,
                    translations = CAST(:translations AS jsonb), display_order = :order, updated_at = now()
                WHERE id = :id AND owner_id IS NOT DISTINCT FROM CAST(:owner AS uuid)""", panParams(row)) == 1;
    }

    /** Deletes a pan of {@code owner} (null = SYSTEM). */
    public boolean deletePan(UUID id, UUID owner) {
        return jdbc.update("DELETE FROM guide_pan_sizes WHERE id = :id AND owner_id IS NOT DISTINCT FROM CAST(:owner AS uuid)",
                new MapSqlParameterSource().addValue("id", id).addValue("owner", owner)) == 1;
    }

    public void upsertConversion(ConversionRow row) {
        IngredientConversion c = row.conversion();
        jdbc.update("""
                INSERT INTO guide_conversions (code, name, grams_per_cup, grams_per_tablespoon, grams_per_teaspoon, translations,
                    display_order, updated_at)
                VALUES (:code, :name, :cup, :tbsp, :tsp, CAST(:translations AS jsonb), :order, now())
                ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, grams_per_cup = EXCLUDED.grams_per_cup,
                    grams_per_tablespoon = EXCLUDED.grams_per_tablespoon, grams_per_teaspoon = EXCLUDED.grams_per_teaspoon,
                    translations = EXCLUDED.translations, display_order = EXCLUDED.display_order, updated_at = now()""",
                new MapSqlParameterSource().addValue("code", c.code()).addValue("name", c.name()).addValue("cup", c.gramsPerCup())
                        .addValue("tbsp", c.gramsPerTablespoon()).addValue("tsp", c.gramsPerTeaspoon())
                        .addValue("translations", json(row.translations())).addValue("order", row.displayOrder()));
    }

    public boolean deleteConversion(String code) {
        return jdbc.update("DELETE FROM guide_conversions WHERE code = :code", Map.of("code", code)) == 1;
    }

    /** Content revision shown to clients (ISO date). */
    public void setRevision(LocalDate revision) {
        jdbc.update("""
                INSERT INTO guide_meta (id, revision) VALUES (1, :revision)
                ON CONFLICT (id) DO UPDATE SET revision = EXCLUDED.revision""", Map.of("revision", revision));
    }

    // ─── mapping ────────────────────────────────────────────────────────────────────────

    private MapSqlParameterSource articleParams(ArticleRow row) {
        return new MapSqlParameterSource().addValue("id", row.id()).addValue("code", row.code()).addValue("category", row.category().name())
                .addValue("title", row.title()).addValue("summary", row.summary()).addValue("icon", row.icon())
                .addValue("tags", json(row.tags())).addValue("blocks", json(row.blocks(), BLOCKS)).addValue("sources", json(row.sources()))
                .addValue("translations", json(row.translations())).addValue("order", row.displayOrder()).addValue("active", row.active());
    }

    private MapSqlParameterSource panParams(PanRow row) {
        PanSizeRequest s = row.size();
        return new MapSqlParameterSource().addValue("id", row.id()).addValue("code", row.code()).addValue("owner", row.ownerId())
                .addValue("shape", s.shape().name()).addValue("name", s.name()).addValue("diameter", s.diameterCm())
                .addValue("length", s.lengthCm()).addValue("width", s.widthCm()).addValue("height", s.heightCm())
                .addValue("volume", s.volumeMl()).addValue("servings", s.servings()).addValue("notes", s.notes())
                .addValue("translations", json(row.translations())).addValue("order", row.displayOrder());
    }

    private ArticleRow article(ResultSet rs) throws SQLException {
        return new ArticleRow(rs.getObject("id", UUID.class), rs.getString("code"), GuideCategory.valueOf(rs.getString("category")),
                rs.getString("title"), rs.getString("summary"), rs.getString("icon"), read(rs.getString("tags"), STRINGS),
                read(rs.getString("blocks"), BLOCKS), read(rs.getString("sources"), STRINGS),
                read(rs.getString("translations"), ARTICLE_TRANSLATIONS), rs.getInt("display_order"), rs.getBoolean("is_active"),
                Sql.instant(rs, "updated_at"));
    }

    private PanRow pan(ResultSet rs) throws SQLException {
        PanSizeRequest size = new PanSizeRequest(PanShape.valueOf(rs.getString("shape")), rs.getString("name"),
                rs.getBigDecimal("diameter_cm"), rs.getBigDecimal("length_cm"), rs.getBigDecimal("width_cm"), rs.getBigDecimal("height_cm"),
                rs.getBigDecimal("volume_ml"), Sql.integer(rs, "servings"), rs.getString("notes"));
        return new PanRow(rs.getObject("id", UUID.class), rs.getString("code"), Sql.uuid(rs, "owner_id"), size,
                read(rs.getString("translations"), PAN_TRANSLATIONS), rs.getInt("display_order"), Sql.instant(rs, "updated_at"));
    }

    private ConversionRow conversion(ResultSet rs) throws SQLException {
        return new ConversionRow(new IngredientConversion(rs.getString("code"), rs.getString("name"), rs.getBigDecimal("grams_per_cup"),
                rs.getBigDecimal("grams_per_tablespoon"), rs.getBigDecimal("grams_per_teaspoon")),
                read(rs.getString("translations"), CONVERSION_TRANSLATIONS), rs.getInt("display_order"), Sql.instant(rs, "updated_at"));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Guide content is not serialisable", e);
        }
    }

    /** With the declared element type: a bare {@code List<GuideBlock>} would lose the {@code type} discriminator to erasure. */
    private String json(Object value, TypeReference<?> type) {
        try {
            return objectMapper.writerFor(type).writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Guide content is not serialisable", e);
        }
    }

    private <T> T read(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored guide content is malformed", e);
        }
    }
}
