package com.ninsky.cronos.kitchen.recipe;

import com.ninsky.cronos.kitchen.costing.CostEngine;
import com.ninsky.cronos.kitchen.costing.CostStatus;
import com.ninsky.cronos.kitchen.costing.FixedCostMethod;
import com.ninsky.cronos.kitchen.shared.Sql;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** JDBC persistence of the recipe aggregate; batch loads keep the ripple at three queries. */
@Repository
@RequiredArgsConstructor
public class RecipeStore {

    private static final String HEAD = """
            SELECT r.id, r.code, r.owner_id, r.name, r.category_id, r.difficulty, r.description, r.process_html, r.storage_instructions,
                   r.shelf_life_days, r.prep_minutes, r.bake_minutes, r.cool_minutes, r.oven_temperature_c, r.yield_quantity, r.yield_unit,
                   r.status, r.target_margin_percent, r.waste_percent, r.ingredients_cost, r.waste_cost, r.fixed_costs, r.total_cost,
                   r.cost_per_unit, r.suggested_unit_price, r.unpriced_lines, r.cost_status, r.cost_calculated_at, r.created_at,
                   r.created_by_id, r.updated_at, r.updated_by_id, r.version
            FROM recipes r""";

    private final NamedParameterJdbcTemplate jdbc;

    /** A live (not deleted) recipe visible to the tenant: own or SYSTEM. */
    public Optional<RecipeAggregate> findVisible(UUID id, UUID tenantId) {
        return heads(HEAD + " WHERE r.id = :id AND r.deleted_at IS NULL AND (r.owner_id IS NULL OR r.owner_id = :tenant)",
                new MapSqlParameterSource().addValue("id", id).addValue("tenant", tenantId))
                .stream().findFirst().map(head -> assemble(List.of(head)).getFirst());
    }

    /** A live recipe regardless of owner (shares, notifications). */
    public Optional<RecipeAggregate> findLive(UUID id) {
        return heads(HEAD + " WHERE r.id = :id AND r.deleted_at IS NULL", new MapSqlParameterSource().addValue("id", id))
                .stream().findFirst().map(head -> assemble(List.of(head)).getFirst());
    }

    /** Locks the tenant's live recipe row for an update. */
    public Optional<RecipeAggregate> lockOwned(UUID id, UUID tenantId) {
        return heads(HEAD + " WHERE r.id = :id AND r.deleted_at IS NULL AND r.owner_id = :tenant FOR UPDATE",
                new MapSqlParameterSource().addValue("id", id).addValue("tenant", tenantId))
                .stream().findFirst().map(head -> assemble(List.of(head)).getFirst());
    }

    /** Live recipes by id, in id order, locked for the ripple. */
    public List<RecipeAggregate> lockAll(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return assemble(heads(HEAD + " WHERE r.id IN (:ids) AND r.deleted_at IS NULL ORDER BY r.id FOR UPDATE",
                new MapSqlParameterSource().addValue("ids", Set.copyOf(ids))));
    }

    /** Heads only (list rows), in the order of {@code ids}. */
    public List<RecipeAggregate.Head> headsInOrder(List<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<UUID, RecipeAggregate.Head> byId = heads(HEAD + " WHERE r.id IN (:ids)", new MapSqlParameterSource().addValue("ids", Set.copyOf(ids)))
                .stream().collect(Collectors.toMap(RecipeAggregate.Head::id, h -> h));
        return ids.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
    }

    public List<UUID> idsUsingIngredient(UUID ingredientId, UUID tenantId) {
        return jdbc.queryForList("""
                SELECT DISTINCT r.id FROM recipe_lines l JOIN recipes r ON r.id = l.recipe_id
                WHERE l.ingredient_id = :ingredient AND r.deleted_at IS NULL AND r.owner_id IS NOT DISTINCT FROM CAST(:tenant AS uuid)""",
                new MapSqlParameterSource().addValue("ingredient", ingredientId).addValue("tenant", tenantId), UUID.class);
    }

    /** Live recipes of any owner using the ingredient. */
    public List<UUID> idsUsingIngredientAnywhere(UUID ingredientId) {
        return jdbc.queryForList("""
                SELECT DISTINCT r.id FROM recipe_lines l JOIN recipes r ON r.id = l.recipe_id
                WHERE l.ingredient_id = :ingredient AND r.deleted_at IS NULL""", Map.of("ingredient", ingredientId), UUID.class);
    }

    public boolean codeTaken(UUID tenantId, String code, UUID excludeId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM recipes WHERE owner_id IS NOT DISTINCT FROM CAST(:tenant AS uuid) AND code = :code
                    AND deleted_at IS NULL AND id IS DISTINCT FROM CAST(:exclude AS uuid))""",
                new MapSqlParameterSource().addValue("tenant", tenantId).addValue("code", code).addValue("exclude", excludeId), Boolean.class));
    }

    public boolean nameTaken(UUID tenantId, String name, UUID excludeId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM recipes WHERE owner_id IS NOT DISTINCT FROM CAST(:tenant AS uuid)
                    AND kitchen_fold(name) = kitchen_fold(:name) AND deleted_at IS NULL AND id IS DISTINCT FROM CAST(:exclude AS uuid))""",
                new MapSqlParameterSource().addValue("tenant", tenantId).addValue("name", name).addValue("exclude", excludeId), Boolean.class));
    }

    public void insert(RecipeAggregate.Head head) {
        jdbc.update("""
                INSERT INTO recipes (id, code, owner_id, name, category_id, difficulty, description, process_html, storage_instructions,
                    shelf_life_days, prep_minutes, bake_minutes, cool_minutes, oven_temperature_c, yield_quantity, yield_unit, status,
                    target_margin_percent, waste_percent, unpriced_lines, cost_status, created_at, created_by_id, updated_at, updated_by_id,
                    version)
                VALUES (:id, :code, :owner, :name, :category, :difficulty, :description, :process, :storage, :shelfLife, :prep, :bake,
                    :cool, :oven, :yield, :yieldUnit, :status, :margin, :waste, 0, 'INCOMPLETE', :now, :actor, :now, :actor, :version)""",
                headParams(head).addValue("code", head.code()).addValue("owner", head.ownerId()).addValue("status", head.status().name())
                        .addValue("now", at(head.createdAt())).addValue("actor", head.createdBy()));
    }

    /** Optimistic update of the editable fields; false = version mismatch. */
    public boolean update(RecipeAggregate.Head head, long expectedVersion) {
        return jdbc.update("""
                UPDATE recipes SET name = :name, category_id = :category, difficulty = :difficulty, description = :description,
                    process_html = :process, storage_instructions = :storage, shelf_life_days = :shelfLife, prep_minutes = :prep,
                    bake_minutes = :bake, cool_minutes = :cool, oven_temperature_c = :oven, yield_quantity = :yield,
                    yield_unit = :yieldUnit, target_margin_percent = :margin, waste_percent = :waste, updated_at = :now,
                    updated_by_id = :actor, version = :version
                WHERE id = :id AND version = :expected AND deleted_at IS NULL""",
                headParams(head).addValue("now", at(head.updatedAt())).addValue("actor", head.updatedBy())
                        .addValue("expected", expectedVersion)) == 1;
    }

    public boolean changeStatus(UUID id, long expectedVersion, RecipeStatus status, UUID actor, Instant now) {
        return jdbc.update("""
                UPDATE recipes SET status = :status, version = version + 1, updated_at = :now, updated_by_id = :actor
                WHERE id = :id AND version = :expected AND deleted_at IS NULL""",
                new MapSqlParameterSource().addValue("id", id).addValue("expected", expectedVersion).addValue("status", status.name())
                        .addValue("now", at(now)).addValue("actor", actor)) == 1;
    }

    /** version + 1 for a change outside the head (files); returns the new version. */
    public long bumpVersion(UUID id, UUID actor, Instant now) {
        Long version = jdbc.queryForObject("""
                UPDATE recipes SET version = version + 1, updated_at = :now, updated_by_id = :actor WHERE id = :id RETURNING version""",
                new MapSqlParameterSource().addValue("id", id).addValue("now", at(now)).addValue("actor", actor), Long.class);
        return version == null ? 0 : version;
    }

    public void softDelete(UUID id, UUID actor, Instant now) {
        jdbc.update("UPDATE recipes SET deleted_at = :now, updated_at = :now, updated_by_id = :actor, version = version + 1 WHERE id = :id",
                new MapSqlParameterSource().addValue("id", id).addValue("now", at(now)).addValue("actor", actor));
    }

    /** Replaces lines and their extra allergens; ids are kept so revisions diff by id. */
    public void replaceLines(UUID recipeId, List<RecipeAggregate.Line> lines) {
        Set<UUID> keep = lines.stream().map(RecipeAggregate.Line::id).collect(Collectors.toSet());
        MapSqlParameterSource recipe = new MapSqlParameterSource().addValue("recipe", recipeId).addValue("keep", keep.isEmpty() ? null : keep);
        jdbc.update(keep.isEmpty() ? "DELETE FROM recipe_lines WHERE recipe_id = :recipe"
                : "DELETE FROM recipe_lines WHERE recipe_id = :recipe AND id NOT IN (:keep)", recipe);
        jdbc.batchUpdate("""
                INSERT INTO recipe_lines (id, recipe_id, ingredient_id, section, quantity, unit_id, optional, quote_selectable, notes,
                    display_order, line_cost)
                VALUES (:id, :recipe, :ingredient, :section, :quantity, :unit, :optional, :selectable, :notes, :order, :cost)
                ON CONFLICT (id) DO UPDATE SET ingredient_id = EXCLUDED.ingredient_id, section = EXCLUDED.section,
                    quantity = EXCLUDED.quantity, unit_id = EXCLUDED.unit_id, optional = EXCLUDED.optional,
                    quote_selectable = EXCLUDED.quote_selectable, notes = EXCLUDED.notes, display_order = EXCLUDED.display_order,
                    line_cost = EXCLUDED.line_cost""",
                lines.stream().map(l -> new MapSqlParameterSource().addValue("id", l.id()).addValue("recipe", recipeId)
                        .addValue("ingredient", l.ingredientId()).addValue("section", l.section()).addValue("quantity", l.quantity())
                        .addValue("unit", l.unitId()).addValue("optional", l.optional()).addValue("selectable", l.quoteSelectable())
                        .addValue("notes", l.notes()).addValue("order", l.displayOrder()).addValue("cost", l.lineCost()))
                        .toArray(SqlParameterSource[]::new));
        if (!keep.isEmpty()) {
            jdbc.update("DELETE FROM recipe_line_allergens WHERE line_id IN (:keep)", recipe);
        }
        jdbc.batchUpdate("INSERT INTO recipe_line_allergens (line_id, allergen_id, source) VALUES (:line, :allergen, :source)",
                lines.stream().flatMap(l -> l.extraAllergens().stream().map(a -> new MapSqlParameterSource().addValue("line", l.id())
                        .addValue("allergen", a.allergenId()).addValue("source", a.source().name()))).toArray(SqlParameterSource[]::new));
    }

    public void replaceFixed(UUID recipeId, List<RecipeAggregate.Fixed> fixed) {
        jdbc.update("DELETE FROM recipe_fixed_costs WHERE recipe_id = :recipe", Map.of("recipe", recipeId));
        jdbc.batchUpdate("""
                INSERT INTO recipe_fixed_costs (id, recipe_id, user_fixed_cost_id, minutes, percentage, cost)
                VALUES (:id, :recipe, :master, :minutes, :percentage, :cost)""",
                fixed.stream().map(f -> new MapSqlParameterSource().addValue("id", f.id()).addValue("recipe", recipeId)
                        .addValue("master", f.userFixedCostId()).addValue("minutes", f.minutes()).addValue("percentage", f.percentage())
                        .addValue("cost", f.cost())).toArray(SqlParameterSource[]::new));
    }

    /** Stores a cost result (header, lines, fixed costs) and sets the version. */
    public void storeCost(UUID recipeId, CostEngine.Result result, Instant at, long version) {
        jdbc.update("""
                UPDATE recipes SET ingredients_cost = :ingredients, waste_cost = :waste, fixed_costs = :fixed, total_cost = :total,
                    cost_per_unit = :perUnit, suggested_unit_price = :suggested, unpriced_lines = :unpriced, cost_status = :status,
                    cost_calculated_at = :at, version = :version
                WHERE id = :id""",
                new MapSqlParameterSource().addValue("id", recipeId).addValue("ingredients", result.ingredientsCost())
                        .addValue("waste", result.wasteCost()).addValue("fixed", result.fixedCosts()).addValue("total", result.totalCost())
                        .addValue("perUnit", result.costPerUnit()).addValue("suggested", result.suggestedUnitPrice())
                        .addValue("unpriced", result.unpricedLines()).addValue("status", result.status().name())
                        .addValue("at", at(at)).addValue("version", version));
        jdbc.batchUpdate("UPDATE recipe_lines SET line_cost = :cost WHERE id = :id",
                result.lines().stream().map(l -> new MapSqlParameterSource().addValue("id", UUID.fromString(l.key())).addValue("cost", l.lineCost()))
                        .toArray(SqlParameterSource[]::new));
        jdbc.batchUpdate("UPDATE recipe_fixed_costs SET cost = :cost WHERE id = :id",
                result.fixed().stream().map(f -> new MapSqlParameterSource().addValue("id", UUID.fromString(f.key())).addValue("cost", f.cost()))
                        .toArray(SqlParameterSource[]::new));
    }

    /** CURRENT → STALE for the given recipes (an input changed but they were not recalculated). */
    public int markStale(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return 0;
        }
        return jdbc.update("UPDATE recipes SET cost_status = 'STALE' WHERE id IN (:ids) AND cost_status = 'CURRENT'",
                Map.of("ids", Set.copyOf(ids)));
    }

    private List<RecipeAggregate.Head> heads(String sql, MapSqlParameterSource params) {
        return jdbc.query(sql, params, HEAD_MAPPER);
    }

    private List<RecipeAggregate> assemble(List<RecipeAggregate.Head> heads) {
        if (heads.isEmpty()) {
            return List.of();
        }
        Set<UUID> ids = heads.stream().map(RecipeAggregate.Head::id).collect(Collectors.toSet());
        Map<String, Object> params = Map.of("ids", ids);

        Map<UUID, List<RecipeAggregate.ExtraAllergen>> extras = new HashMap<>();
        jdbc.query("""
                SELECT a.line_id, a.allergen_id, a.source FROM recipe_line_allergens a JOIN recipe_lines l ON l.id = a.line_id
                WHERE l.recipe_id IN (:ids) ORDER BY a.allergen_id""", params, rs -> {
            extras.computeIfAbsent(rs.getObject("line_id", UUID.class), k -> new ArrayList<>())
                    .add(new RecipeAggregate.ExtraAllergen(rs.getLong("allergen_id"), AllergenSource.valueOf(rs.getString("source"))));
        });
        Map<UUID, List<RecipeAggregate.Line>> lines = new HashMap<>();
        jdbc.query("""
                SELECT id, recipe_id, ingredient_id, section, quantity, unit_id, optional, quote_selectable, notes, display_order, line_cost
                FROM recipe_lines WHERE recipe_id IN (:ids) ORDER BY display_order, id""", params, rs -> {
            UUID lineId = rs.getObject("id", UUID.class);
            lines.computeIfAbsent(rs.getObject("recipe_id", UUID.class), k -> new ArrayList<>()).add(new RecipeAggregate.Line(lineId,
                    rs.getObject("ingredient_id", UUID.class), rs.getString("section"), rs.getBigDecimal("quantity"), rs.getLong("unit_id"),
                    rs.getBoolean("optional"), rs.getBoolean("quote_selectable"), rs.getString("notes"), rs.getInt("display_order"),
                    rs.getBigDecimal("line_cost"), extras.getOrDefault(lineId, List.of())));
        });
        Map<UUID, List<RecipeAggregate.Fixed>> fixed = new HashMap<>();
        jdbc.query("""
                SELECT f.id, f.recipe_id, f.user_fixed_cost_id, m.name, m.calculation_method, m.default_amount, m.percentage AS master_percentage,
                       f.minutes, f.percentage, f.cost
                FROM recipe_fixed_costs f JOIN user_fixed_costs m ON m.id = f.user_fixed_cost_id
                WHERE f.recipe_id IN (:ids) ORDER BY m.name, f.id""", params, rs -> {
            fixed.computeIfAbsent(rs.getObject("recipe_id", UUID.class), k -> new ArrayList<>()).add(new RecipeAggregate.Fixed(
                    rs.getObject("id", UUID.class), rs.getObject("user_fixed_cost_id", UUID.class), rs.getString("name"),
                    FixedCostMethod.parse(rs.getString("calculation_method")).orElse(FixedCostMethod.FIXED_PER_BATCH),
                    rs.getBigDecimal("default_amount"), rs.getBigDecimal("master_percentage"), Sql.integer(rs, "minutes"),
                    rs.getBigDecimal("percentage"), rs.getBigDecimal("cost")));
        });
        return heads.stream()
                .map(h -> new RecipeAggregate(h, lines.getOrDefault(h.id(), List.of()), fixed.getOrDefault(h.id(), List.of())))
                .toList();
    }

    private static MapSqlParameterSource headParams(RecipeAggregate.Head head) {
        return new MapSqlParameterSource().addValue("id", head.id()).addValue("name", head.name()).addValue("category", head.categoryId())
                .addValue("difficulty", head.difficulty().name()).addValue("description", head.description())
                .addValue("process", head.processHtml()).addValue("storage", head.storageInstructions())
                .addValue("shelfLife", head.shelfLifeDays()).addValue("prep", head.prepMinutes()).addValue("bake", head.bakeMinutes())
                .addValue("cool", head.coolMinutes()).addValue("oven", head.ovenTemperatureC()).addValue("yield", head.yieldQuantity())
                .addValue("yieldUnit", head.yieldUnit()).addValue("margin", head.targetMarginPercent()).addValue("waste", head.wastePercent())
                .addValue("version", head.version());
    }

    private static final RowMapper<RecipeAggregate.Head> HEAD_MAPPER = RecipeStore::head;

    private static RecipeAggregate.Head head(ResultSet rs, int row) throws SQLException {
        RecipeAggregate.Cost cost = new RecipeAggregate.Cost(rs.getBigDecimal("ingredients_cost"), rs.getBigDecimal("waste_cost"),
                rs.getBigDecimal("fixed_costs"), rs.getBigDecimal("total_cost"), rs.getBigDecimal("cost_per_unit"),
                rs.getBigDecimal("suggested_unit_price"), rs.getInt("unpriced_lines"), CostStatus.valueOf(rs.getString("cost_status")),
                Sql.instant(rs, "cost_calculated_at"));
        return new RecipeAggregate.Head(rs.getObject("id", UUID.class), rs.getString("code"), Sql.uuid(rs, "owner_id"), rs.getString("name"),
                (Long) rs.getObject("category_id", Long.class), Difficulty.valueOf(rs.getString("difficulty")), rs.getString("description"),
                rs.getString("process_html"), rs.getString("storage_instructions"), Sql.integer(rs, "shelf_life_days"),
                Sql.integer(rs, "prep_minutes"), Sql.integer(rs, "bake_minutes"), Sql.integer(rs, "cool_minutes"),
                Sql.integer(rs, "oven_temperature_c"), rs.getBigDecimal("yield_quantity"), rs.getString("yield_unit"),
                RecipeStatus.valueOf(rs.getString("status")), rs.getBigDecimal("target_margin_percent"), rs.getBigDecimal("waste_percent"),
                cost, Sql.instant(rs, "created_at"), Sql.uuid(rs, "created_by_id"), Sql.instant(rs, "updated_at"),
                Sql.uuid(rs, "updated_by_id"), rs.getLong("version"));
    }

    static OffsetDateTime at(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
