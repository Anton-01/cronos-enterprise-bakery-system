package com.ninsky.cronos.kitchen.allergen;

import com.ninsky.cronos.kitchen.shared.KitchenStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Allergen writes and usage counts; reads go through the cached {@link AllergenCatalog}. */
@Repository
@RequiredArgsConstructor
public class AllergenRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public long insert(String code, UUID ownerId, String icon, List<String> regulations, UUID actor, Instant now) {
        Long id = jdbc.queryForObject("""
                INSERT INTO allergens (code, owner_id, icon, regulations, status, version, created_at, updated_at, updated_by)
                VALUES (:code, :owner, :icon, CAST(:regulations AS varchar[]), 'ACTIVE', 0, :now, :now, :actor) RETURNING id""",
                new MapSqlParameterSource().addValue("code", code).addValue("owner", ownerId).addValue("icon", icon)
                        .addValue("regulations", regulations.toArray(String[]::new)).addValue("actor", actor).addValue("now", at(now)),
                Long.class);
        return id == null ? 0L : id;
    }

    /** Bumps the version when it still matches; false = concurrent modification. */
    public boolean update(long id, long version, String icon, List<String> regulations, UUID actor, Instant now) {
        return jdbc.update("""
                UPDATE allergens SET icon = :icon, regulations = CAST(:regulations AS varchar[]), version = version + 1,
                       updated_at = :now, updated_by = :actor
                WHERE id = :id AND version = :version""",
                new MapSqlParameterSource().addValue("id", id).addValue("version", version).addValue("icon", icon)
                        .addValue("regulations", regulations.toArray(String[]::new)).addValue("actor", actor).addValue("now", at(now))) == 1;
    }

    public boolean touch(long id, long version, UUID actor, Instant now) {
        return jdbc.update("""
                UPDATE allergens SET version = version + 1, updated_at = :now, updated_by = :actor WHERE id = :id AND version = :version""",
                Map.of("id", id, "version", version, "actor", actor, "now", at(now))) == 1;
    }

    public boolean changeStatus(long id, long version, KitchenStatus status, UUID actor, Instant now) {
        return jdbc.update("""
                UPDATE allergens SET status = :status, version = version + 1, updated_at = :now, updated_by = :actor
                WHERE id = :id AND version = :version""",
                Map.of("id", id, "version", version, "status", status.name(), "actor", actor, "now", at(now))) == 1;
    }

    public void upsertText(long id, Collection<String> locales, String name, String description) {
        SqlParameterSource[] batch = locales.stream()
                .map(locale -> new MapSqlParameterSource().addValue("id", id).addValue("locale", locale)
                        .addValue("name", name).addValue("description", description))
                .toArray(SqlParameterSource[]::new);
        jdbc.batchUpdate("""
                INSERT INTO allergen_i18n (allergen_id, locale, name, description) VALUES (:id, :locale, :name, :description)
                ON CONFLICT (allergen_id, locale) DO UPDATE SET name = EXCLUDED.name, description = EXCLUDED.description""", batch);
    }

    /** Replaces the keywords {@code ownerId} added to {@code id} (null owner = platform keywords). */
    public void replaceKeywords(long id, UUID ownerId, String locale, Collection<String> keywords) {
        MapSqlParameterSource owner = new MapSqlParameterSource().addValue("id", id).addValue("owner", ownerId);
        jdbc.update("DELETE FROM allergen_keywords WHERE allergen_id = :id AND owner_id IS NOT DISTINCT FROM CAST(:owner AS uuid)", owner);
        SqlParameterSource[] batch = keywords.stream()
                .map(k -> new MapSqlParameterSource().addValue("id", id).addValue("owner", ownerId).addValue("locale", locale).addValue("keyword", k))
                .toArray(SqlParameterSource[]::new);
        jdbc.batchUpdate("""
                INSERT INTO allergen_keywords (allergen_id, owner_id, locale, keyword) VALUES (:id, :owner, :locale, :keyword)
                ON CONFLICT DO NOTHING""", batch);
    }

    public void delete(long id) {
        jdbc.update("DELETE FROM allergens WHERE id = :id", Map.of("id", id));
    }

    /** allergenId → ingredients visible to the tenant that declare it. */
    public Map<Long, Long> ingredientCounts(UUID tenantId) {
        Map<Long, Long> counts = new HashMap<>();
        jdbc.query("""
                SELECT ia.allergen_id, count(*) AS n FROM ingredient_allergens ia
                JOIN ingredients i ON i.id = ia.ingredient_id
                WHERE i.owner_id IS NULL OR i.owner_id = :tenant GROUP BY ia.allergen_id""",
                Map.of("tenant", tenantId), rs -> {
                    counts.put(rs.getLong("allergen_id"), rs.getLong("n"));
                });
        return counts;
    }

    /** Referenced by any ingredient or recipe line (any tenant). */
    public boolean inUse(long id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM ingredient_allergens WHERE allergen_id = :id)
                    OR EXISTS (SELECT 1 FROM recipe_line_allergens WHERE allergen_id = :id)""", Map.of("id", id), Boolean.class));
    }

    private static OffsetDateTime at(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
