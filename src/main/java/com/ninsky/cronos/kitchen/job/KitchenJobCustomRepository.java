package com.ninsky.cronos.kitchen.job;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** {@code kitchen_jobs} outbox SQL; claims use SKIP LOCKED so instances never collide. */
@Repository
@RequiredArgsConstructor
public class KitchenJobCustomRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /** A locked pending row with its raw JSON payload. */
    public record Row(long id, String payload, int attempts) {
    }

    public void insert(String kind, String payload) {
        jdbc.update("INSERT INTO kitchen_jobs (kind, payload) VALUES (:kind, CAST(:payload AS jsonb))",
                new MapSqlParameterSource().addValue("kind", kind).addValue("payload", payload));
    }

    /** Locks the oldest pending job for the current transaction. */
    public Optional<Row> claim() {
        return jdbc.query("""
                SELECT id, payload, attempts FROM kitchen_jobs WHERE status = 'PENDING' ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED""",
                Map.of(), (rs, i) -> new Row(rs.getLong("id"), rs.getString("payload"), rs.getInt("attempts"))).stream().findFirst();
    }

    public void done(long id) {
        jdbc.update("UPDATE kitchen_jobs SET status = 'DONE', processed_at = now() WHERE id = :id", Map.of("id", id));
    }

    /** Records a failure; the job turns FAILED once {@code attempts} reaches {@code max}. */
    public void failed(long id, int attempts, String error, int max) {
        jdbc.update("""
                UPDATE kitchen_jobs SET attempts = :attempts, last_error = left(:error, 500),
                    status = CASE WHEN :attempts >= :max THEN 'FAILED' ELSE 'PENDING' END,
                    processed_at = CASE WHEN :attempts >= :max THEN now() END
                WHERE id = :id""",
                new MapSqlParameterSource().addValue("id", id).addValue("attempts", attempts).addValue("error", error)
                        .addValue("max", max));
    }

    /** Live recipes using the ingredient whose owner has no own price for it (reference-price ripple). */
    public List<UUID> recipesWithoutOwnPrice(UUID ingredientId) {
        return jdbc.queryForList("""
                SELECT DISTINCT r.id FROM recipe_lines l JOIN recipes r ON r.id = l.recipe_id
                WHERE l.ingredient_id = :ingredient AND r.deleted_at IS NULL
                  AND NOT EXISTS (SELECT 1 FROM ingredient_prices p WHERE p.ingredient_id = :ingredient AND p.owner_id = r.owner_id)""",
                new MapSqlParameterSource().addValue("ingredient", ingredientId), UUID.class);
    }
}
