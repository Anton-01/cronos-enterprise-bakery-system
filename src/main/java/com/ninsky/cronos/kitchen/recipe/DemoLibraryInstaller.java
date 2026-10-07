package com.ninsky.cronos.kitchen.recipe;

import com.ninsky.cronos.kitchen.job.KitchenJobs;
import com.ninsky.cronos.kitchen.job.RecalculationJob;
import com.ninsky.cronos.kitchen.shared.KitchenProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Copies the SYSTEM recipe library into the demo tenant ({@code app.kitchen.demo-tenant} = username,
 * §10.5) once; copies already present (same code or name) are skipped, so restarts are no-ops.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DemoLibraryInstaller implements ApplicationRunner {

    private final KitchenProperties properties;
    private final NamedParameterJdbcTemplate jdbc;
    private final KitchenJobs jobs;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Optional.ofNullable(properties.demoTenant()).filter(s -> !s.isBlank()).flatMap(this::tenant).ifPresent(this::install);
    }

    private Optional<UUID> tenant(String username) {
        return jdbc.queryForList("SELECT id FROM users WHERE username = :username", new MapSqlParameterSource("username", username), UUID.class)
                .stream().findFirst();
    }

    private void install(UUID tenant) {
        MapSqlParameterSource params = new MapSqlParameterSource("tenant", tenant);
        List<UUID> ids = jdbc.queryForList("""
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
                RETURNING id""", params, UUID.class);
        if (ids.isEmpty()) {
            return;
        }
        jdbc.update("""
                INSERT INTO recipe_lines (id, recipe_id, ingredient_id, section, quantity, unit_id, optional, quote_selectable, notes, display_order)
                SELECT gen_random_uuid(), c.id, l.ingredient_id, l.section, l.quantity, l.unit_id, l.optional, l.quote_selectable, l.notes,
                       l.display_order
                FROM recipes c
                JOIN recipes s ON s.owner_id IS NULL AND s.deleted_at IS NULL AND s.code = c.code
                JOIN recipe_lines l ON l.recipe_id = s.id
                WHERE c.id IN (:ids)""", params.addValue("ids", ids));
        jobs.enqueue(new RecalculationJob(null, ids, "kitchen.revision.seeded", List.of(), tenant));
        log.info("Installed {} library recipes for the demo tenant", ids.size());
    }
}
