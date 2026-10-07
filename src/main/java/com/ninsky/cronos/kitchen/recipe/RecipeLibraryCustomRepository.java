package com.ninsky.cronos.kitchen.recipe;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** SQL that copies the SYSTEM recipe library into a tenant (§10.5). */
@Repository
@RequiredArgsConstructor
public class RecipeLibraryCustomRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public Optional<UUID> userIdByUsername(String username) {
        return jdbc.queryForList("SELECT id FROM users WHERE username = :username", new MapSqlParameterSource("username", username), UUID.class)
                .stream().findFirst();
    }

    /** Copies library recipes the tenant lacks (same code or folded name); returns the new ids. */
    public List<UUID> copyRecipes(UUID tenant) {
        return jdbc.queryForList("""
                INSERT INTO recipes (id, code, owner_id, name, category_id, difficulty, description, process_html, storage_instructions,
                                     yield_quantity, yield_unit, prep_minutes, bake_minutes, cool_minutes, oven_temperature_c, shelf_life_days,
                                     status, target_margin_percent, waste_percent, cost_status, version, created_at, created_by_id)
                SELECT gen_random_uuid(), r.code, :tenant, r.name, r.category_id, r.difficulty, r.description, r.process_html,
                       r.storage_instructions, r.yield_quantity, r.yield_unit, r.prep_minutes, r.bake_minutes, r.cool_minutes,
                       r.oven_temperature_c, r.shelf_life_days, 'ACTIVE', r.target_margin_percent, r.waste_percent, 'STALE', 0, now(), :tenant
                FROM recipes r
                WHERE r.owner_id IS NULL AND r.deleted_at IS NULL
                  AND NOT EXISTS (SELECT 1 FROM recipes o WHERE o.owner_id = :tenant AND o.deleted_at IS NULL
                                  AND (o.code = r.code OR kitchen_fold(o.name) = kitchen_fold(r.name)))
                RETURNING id""", new MapSqlParameterSource("tenant", tenant), UUID.class);
    }

    /** Copies the library lines into the given copies, matched by recipe code. */
    public void copyLines(List<UUID> copyIds) {
        jdbc.update("""
                INSERT INTO recipe_lines (id, recipe_id, ingredient_id, section, quantity, unit_id, optional, quote_selectable, notes, display_order)
                SELECT gen_random_uuid(), c.id, l.ingredient_id, l.section, l.quantity, l.unit_id, l.optional, l.quote_selectable, l.notes,
                       l.display_order
                FROM recipes c
                JOIN recipes s ON s.owner_id IS NULL AND s.deleted_at IS NULL AND s.code = c.code
                JOIN recipe_lines l ON l.recipe_id = s.id
                WHERE c.id IN (:ids)""", new MapSqlParameterSource("ids", copyIds));
    }
}
