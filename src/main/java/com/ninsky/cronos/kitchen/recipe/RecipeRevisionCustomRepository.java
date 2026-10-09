package com.ninsky.cronos.kitchen.recipe;

import com.ninsky.cronos.finance.shared.UserRef;
import com.ninsky.cronos.finance.shared.UserRefCustomRepository;
import com.ninsky.cronos.kitchen.shared.Sql;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** {@code recipe_revisions} SQL; payloads arrive and leave as JSON text. */
@Repository
@RequiredArgsConstructor
public class RecipeRevisionCustomRepository {

    /** API sort field → SQL; revisions only sort by version. */
    public static final Map<String, String> SORTS = Map.of("version", "rv.version");

    private final NamedParameterJdbcTemplate jdbc;
    private final UserRefCustomRepository userRefs;

    public record Row(long version, Instant changedAt, UserRef changedBy, String summaryKey, String summaryParams, String changes,
                      BigDecimal costPerUnit) {
    }

    public void insert(UUID recipeId, long version, UUID actor, Instant at, String key, String paramsJson, String changesJson,
                       BigDecimal costPerUnit) {
        jdbc.update("""
                INSERT INTO recipe_revisions (recipe_id, version, changed_at, changed_by, summary_key, summary_params, changes, cost_per_unit)
                VALUES (:recipe, :version, :at, :actor, :key, CAST(:params AS jsonb), CAST(:changes AS jsonb), :cost)
                ON CONFLICT (recipe_id, version) DO NOTHING""",
                new MapSqlParameterSource().addValue("recipe", recipeId).addValue("version", version)
                        .addValue("at", RecipeCustomRepository.at(at)).addValue("actor", actor).addValue("key", key)
                        .addValue("params", paramsJson).addValue("changes", changesJson).addValue("cost", costPerUnit));
    }

    /** Bumps every live recipe using the ingredient and writes one revision each; returns the recipes touched. */
    public List<UUID> bumpRecipesUsing(UUID ingredientId, UUID actor, Instant at, String key, String paramsJson, String changesJson) {
        return jdbc.queryForList("""
                WITH bumped AS (
                    UPDATE recipes r SET version = r.version + 1
                    WHERE r.deleted_at IS NULL AND EXISTS (SELECT 1 FROM recipe_lines l WHERE l.recipe_id = r.id AND l.ingredient_id = :ingredient)
                    RETURNING r.id, r.version, r.cost_per_unit)
                INSERT INTO recipe_revisions (recipe_id, version, changed_at, changed_by, summary_key, summary_params, changes, cost_per_unit)
                SELECT id, version, :at, :actor, :key, CAST(:params AS jsonb), CAST(:changes AS jsonb), cost_per_unit FROM bumped
                RETURNING recipe_id""",
                new MapSqlParameterSource().addValue("ingredient", ingredientId).addValue("at", RecipeCustomRepository.at(at))
                        .addValue("actor", actor).addValue("key", key).addValue("params", paramsJson).addValue("changes", changesJson),
                UUID.class);
    }

    /**
     * Whether every version in {@code (since, until]} has a revision whose key is in {@code keys} (a missing revision
     * row counts as "something else").
     */
    public boolean onlyKeysBetween(UUID recipeId, long since, long until, java.util.Collection<String> keys) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT count(*) FILTER (WHERE summary_key IN (:keys)) = :span FROM recipe_revisions
                WHERE recipe_id = :recipe AND version > :since AND version <= :until""",
                new MapSqlParameterSource().addValue("recipe", recipeId).addValue("since", since).addValue("until", until)
                        .addValue("span", until - since).addValue("keys", keys), Boolean.class));
    }

    public long count(UUID recipeId) {
        Long total = jdbc.queryForObject("SELECT count(*) FROM recipe_revisions WHERE recipe_id = :recipe",
                new MapSqlParameterSource("recipe", recipeId), Long.class);
        return total == null ? 0 : total;
    }

    /** Newest first. */
    public List<Row> page(UUID recipeId, int limit, long offset) {
        return jdbc.query("SELECT rv.version, rv.changed_at, rv.summary_key, rv.summary_params, rv.changes, rv.cost_per_unit, "
                        + UserRefCustomRepository.columns("rv.changed_by") + " FROM recipe_revisions rv" + UserRefCustomRepository.join("rv.changed_by")
                        + " WHERE rv.recipe_id = :recipe ORDER BY rv.version DESC LIMIT :limit OFFSET :offset",
                new MapSqlParameterSource().addValue("recipe", recipeId).addValue("limit", limit).addValue("offset", offset),
                (rs, i) -> new Row(rs.getLong("version"), Sql.instant(rs, "changed_at"), userRefs.map(rs), rs.getString("summary_key"),
                        rs.getString("summary_params"), rs.getString("changes"), rs.getBigDecimal("cost_per_unit")));
    }
}
