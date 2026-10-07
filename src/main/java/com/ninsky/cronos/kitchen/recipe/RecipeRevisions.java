package com.ninsky.cronos.kitchen.recipe;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ninsky.cronos.finance.shared.UserRefMapper;
import com.ninsky.cronos.infrastructure.web.paging.CatalogPage;
import com.ninsky.cronos.infrastructure.web.paging.PageQuery;
import com.ninsky.cronos.kitchen.shared.KitchenMessages;
import com.ninsky.cronos.kitchen.shared.Sql;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Append-only recipe history (§5.8); summaries are stored as key + args and localised on read. */
@Component
@RequiredArgsConstructor
public class RecipeRevisions {

    private static final Map<String, String> SORTS = Map.of("version", "rv.version");
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final UserRefMapper userRefs;
    private final KitchenMessages messages;

    /** Why a revision was written: an i18n key under {@code kitchen.revision.*} and its arguments. */
    public record Reason(String key, List<Object> args) {
        public Reason {
            args = List.copyOf(args);
        }

        public static Reason of(String key, Object... args) {
            return new Reason(key, List.of(args));
        }
    }

    public void write(UUID recipeId, long version, UUID actor, Instant at, Reason reason, Map<String, Object> changes, BigDecimal costPerUnit) {
        jdbc.update("""
                INSERT INTO recipe_revisions (recipe_id, version, changed_at, changed_by, summary_key, summary_params, changes, cost_per_unit)
                VALUES (:recipe, :version, :at, :actor, :key, CAST(:params AS jsonb), CAST(:changes AS jsonb), :cost)
                ON CONFLICT (recipe_id, version) DO NOTHING""",
                new MapSqlParameterSource().addValue("recipe", recipeId).addValue("version", version).addValue("at", RecipeStore.at(at))
                        .addValue("actor", actor).addValue("key", reason.key()).addValue("params", json(Map.of("args", reason.args())))
                        .addValue("changes", json(changes)).addValue("cost", costPerUnit));
    }

    /**
     * Derived allergens of every live recipe using {@code ingredientId} changed: one set-based version bump
     * and revision per recipe. Returns the recipes touched.
     */
    public List<UUID> allergensChanged(UUID ingredientId, String ingredientName, UUID actor, Instant at) {
        return jdbc.queryForList("""
                WITH bumped AS (
                    UPDATE recipes r SET version = r.version + 1
                    WHERE r.deleted_at IS NULL AND EXISTS (SELECT 1 FROM recipe_lines l WHERE l.recipe_id = r.id AND l.ingredient_id = :ingredient)
                    RETURNING r.id, r.version, r.cost_per_unit)
                INSERT INTO recipe_revisions (recipe_id, version, changed_at, changed_by, summary_key, summary_params, changes, cost_per_unit)
                SELECT id, version, :at, :actor, :key, CAST(:params AS jsonb), CAST(:changes AS jsonb), cost_per_unit FROM bumped
                RETURNING recipe_id""",
                new MapSqlParameterSource().addValue("ingredient", ingredientId).addValue("at", RecipeStore.at(at)).addValue("actor", actor)
                        .addValue("key", "kitchen.revision.allergensChanged").addValue("params", json(Map.of("args", List.of(ingredientName))))
                        .addValue("changes", json(Map.of("allergens", Map.of("ingredient", ingredientName)))),
                UUID.class);
    }

    public CatalogPage<RecipeRevision> page(UUID recipeId, Integer page, Integer size) {
        PageQuery query = PageQuery.of(page, size, null, SORTS, "version,desc");
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("recipe", recipeId)
                .addValue("limit", query.size()).addValue("offset", query.offset());
        Long total = jdbc.queryForObject("SELECT count(*) FROM recipe_revisions WHERE recipe_id = :recipe", params, Long.class);
        List<RecipeRevision> content = jdbc.query("SELECT rv.version, rv.changed_at, rv.summary_key, rv.summary_params, rv.changes, rv.cost_per_unit, "
                        + UserRefMapper.columns("rv.changed_by") + " FROM recipe_revisions rv" + UserRefMapper.join("rv.changed_by")
                        + " WHERE rv.recipe_id = :recipe ORDER BY rv.version DESC LIMIT :limit OFFSET :offset", params,
                (rs, i) -> {
                    Map<String, Object> summaryParams = read(rs.getString("summary_params"));
                    Object args = summaryParams.getOrDefault("args", List.of());
                    Object[] values = args instanceof List<?> list ? list.toArray() : new Object[0];
                    return new RecipeRevision(rs.getLong("version"), Sql.instant(rs, "changed_at"), userRefs.map(rs),
                            messages.get(rs.getString("summary_key"), values), read(rs.getString("changes")), rs.getBigDecimal("cost_per_unit"));
                });
        return CatalogPage.of(content, query, total == null ? 0 : total);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Revision payload is not serialisable", e);
        }
    }

    private Map<String, Object> read(String value) {
        try {
            return value == null ? Map.of() : objectMapper.readValue(value, MAP);
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }
}
