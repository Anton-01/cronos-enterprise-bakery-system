package com.ninsky.cronos.kitchen.ingredient;

import com.ninsky.cronos.kitchen.shared.Dimension;
import com.ninsky.cronos.kitchen.shared.KitchenStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Ingredient writes: head, texts, allergens, substitutes and append-only prices. */
@Repository
@RequiredArgsConstructor
public class IngredientCustomRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /** Editable head fields. */
    public record Head(UUID id, String code, UUID ownerId, long categoryId, Dimension baseDimension, BigDecimal yieldPercent,
                       BigDecimal densityGPerMl, String brand) {
    }

    /** A new price row; {@code ownerId} null = platform reference price. */
    public record NewPrice(UUID id, UUID ingredientId, UUID ownerId, BigDecimal purchaseQuantity, long purchaseUnitId, BigDecimal price,
                           String currency, String supplier, LocalDate pricedAt, BigDecimal costPerBaseUnit) {
    }

    public void insert(Head head, UUID actor, Instant now) {
        jdbc.update("""
                INSERT INTO ingredients (id, code, owner_id, category_id, base_dimension, yield_percent, density_g_per_ml, brand, status,
                    version, created_at, created_by, updated_at, updated_by)
                VALUES (:id, :code, :owner, :category, :dimension, :yield, :density, :brand, 'ACTIVE', 0, :now, :actor, :now, :actor)""",
                params(head).addValue("actor", actor).addValue("now", at(now)));
    }

    public boolean update(Head head, long expectedVersion, UUID actor, Instant now) {
        return jdbc.update("""
                UPDATE ingredients SET category_id = :category, base_dimension = :dimension, yield_percent = :yield,
                    density_g_per_ml = :density, brand = :brand, version = version + 1, updated_at = :now, updated_by = :actor
                WHERE id = :id AND version = :expected""",
                params(head).addValue("expected", expectedVersion).addValue("actor", actor).addValue("now", at(now))) == 1;
    }

    public boolean changeStatus(UUID id, long expectedVersion, KitchenStatus status, UUID actor, Instant now) {
        return jdbc.update("""
                UPDATE ingredients SET status = :status, version = version + 1, updated_at = :now, updated_by = :actor
                WHERE id = :id AND version = :expected""",
                new MapSqlParameterSource().addValue("id", id).addValue("expected", expectedVersion).addValue("status", status.name())
                        .addValue("actor", actor).addValue("now", at(now))) == 1;
    }

    public void delete(UUID id) {
        jdbc.update("DELETE FROM ingredients WHERE id = :id", Map.of("id", id));
    }

    public void upsertTexts(UUID id, Collection<String> locales, String name, String description) {
        jdbc.batchUpdate("""
                INSERT INTO ingredient_i18n (ingredient_id, locale, name, description) VALUES (:id, :locale, :name, :description)
                ON CONFLICT (ingredient_id, locale) DO UPDATE SET name = EXCLUDED.name, description = EXCLUDED.description""",
                locales.stream().map(l -> new MapSqlParameterSource().addValue("id", id).addValue("locale", l).addValue("name", name)
                        .addValue("description", description)).toArray(SqlParameterSource[]::new));
    }

    public void replaceAllergens(UUID id, Collection<Long> allergenIds) {
        jdbc.update("DELETE FROM ingredient_allergens WHERE ingredient_id = :id", Map.of("id", id));
        jdbc.batchUpdate("INSERT INTO ingredient_allergens (ingredient_id, allergen_id) VALUES (:id, :allergen)",
                allergenIds.stream().map(a -> new MapSqlParameterSource().addValue("id", id).addValue("allergen", a))
                        .toArray(SqlParameterSource[]::new));
    }

    /** Replaces the substitutes {@code ownerId} declared on {@code id} (null = platform). */
    public void replaceSubstitutes(UUID id, UUID ownerId, List<IngredientRequest.Substitute> substitutes) {
        jdbc.update("DELETE FROM ingredient_substitutes WHERE ingredient_id = :id AND owner_id IS NOT DISTINCT FROM CAST(:owner AS uuid)",
                new MapSqlParameterSource().addValue("id", id).addValue("owner", ownerId));
        jdbc.batchUpdate("""
                INSERT INTO ingredient_substitutes (ingredient_id, substitute_id, owner_id, ratio, notes)
                VALUES (:id, :substitute, :owner, :ratio, :notes)""",
                substitutes.stream().map(s -> new MapSqlParameterSource().addValue("id", id).addValue("substitute", s.ingredientId())
                        .addValue("owner", ownerId).addValue("ratio", s.ratio()).addValue("notes", s.notes()))
                        .toArray(SqlParameterSource[]::new));
    }

    public void insertPrice(NewPrice price, UUID actor, Instant now) {
        jdbc.update("""
                INSERT INTO ingredient_prices (id, ingredient_id, owner_id, purchase_quantity, purchase_unit_id, price, currency, supplier,
                    priced_at, cost_per_base_unit, recorded_at, recorded_by)
                VALUES (:id, :ingredient, :owner, :quantity, :unit, :price, :currency, :supplier, :pricedAt, :cost, :now, :actor)""",
                new MapSqlParameterSource().addValue("id", price.id()).addValue("ingredient", price.ingredientId())
                        .addValue("owner", price.ownerId()).addValue("quantity", price.purchaseQuantity())
                        .addValue("unit", price.purchaseUnitId()).addValue("price", price.price()).addValue("currency", price.currency())
                        .addValue("supplier", price.supplier()).addValue("pricedAt", price.pricedAt())
                        .addValue("cost", price.costPerBaseUnit()).addValue("now", at(now)).addValue("actor", actor));
    }

    /**
     * Yield or density changed: re-appends every owner's latest price with the cost per base unit recomputed
     * (history stays append-only). Rows whose unit no longer converts are skipped. Returns the owners repriced.
     */
    public List<UUID> repriceLatest(UUID id, UUID actor, Instant now) {
        return jdbc.query("""
                WITH latest AS (
                    SELECT DISTINCT ON (p.owner_id) p.* FROM ingredient_prices p WHERE p.ingredient_id = :id
                    ORDER BY p.owner_id, p.priced_at DESC, p.recorded_at DESC),
                converted AS (
                    SELECT l.*, l.purchase_quantity * mu.multiplier_to_base * CASE
                        WHEN ut.dimension = i.base_dimension THEN 1
                        WHEN i.base_dimension = 'MASS' AND ut.dimension = 'VOLUME' THEN i.density_g_per_ml
                        WHEN i.base_dimension = 'VOLUME' AND ut.dimension = 'MASS' THEN 1 / i.density_g_per_ml END AS base_qty,
                        i.yield_percent
                    FROM latest l JOIN ingredients i ON i.id = l.ingredient_id
                    JOIN measurement_units mu ON mu.id = l.purchase_unit_id JOIN unit_types ut ON ut.id = mu.unit_type_id)
                INSERT INTO ingredient_prices (id, ingredient_id, owner_id, purchase_quantity, purchase_unit_id, price, currency, supplier,
                    priced_at, cost_per_base_unit, recorded_at, recorded_by)
                SELECT gen_random_uuid(), ingredient_id, owner_id, purchase_quantity, purchase_unit_id, price, currency, supplier, priced_at,
                       round(price / (base_qty * yield_percent / 100), 8), :now, :actor
                FROM converted WHERE base_qty IS NOT NULL AND base_qty > 0
                RETURNING owner_id""",
                new MapSqlParameterSource().addValue("id", id).addValue("now", at(now)).addValue("actor", actor),
                (rs, i) -> rs.getObject("owner_id", UUID.class));
    }

    private static MapSqlParameterSource params(Head head) {
        return new MapSqlParameterSource().addValue("id", head.id()).addValue("code", head.code()).addValue("owner", head.ownerId())
                .addValue("category", head.categoryId()).addValue("dimension", head.baseDimension().name())
                .addValue("yield", head.yieldPercent()).addValue("density", head.densityGPerMl()).addValue("brand", head.brand());
    }

    private static OffsetDateTime at(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
