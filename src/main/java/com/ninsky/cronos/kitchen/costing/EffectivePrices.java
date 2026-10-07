package com.ninsky.cronos.kitchen.costing;

import com.ninsky.cronos.kitchen.shared.Dimension;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Batched effective prices (§2): one {@code DISTINCT ON} query per call, own price first, then the
 * reference. Not cached: prices change by tenant action and must be exact (K1).
 */
@Component
@RequiredArgsConstructor
public class EffectivePrices {

    /** Effective price per ingredient for the tenant's ordering of sources. */
    public static final String LATERAL = """
            LEFT JOIN LATERAL (SELECT p.cost_per_base_unit, p.priced_at, p.owner_id FROM ingredient_prices p
                WHERE p.ingredient_id = i.id AND (p.owner_id = :tenant OR p.owner_id IS NULL)
                ORDER BY (p.owner_id IS NULL), p.priced_at DESC, p.recorded_at DESC LIMIT 1) ep ON TRUE""";

    private final NamedParameterJdbcTemplate jdbc;

    public Map<UUID, EffectivePrice> find(UUID tenantId, Collection<UUID> ingredientIds) {
        if (ingredientIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, EffectivePrice> prices = new HashMap<>();
        jdbc.query("""
                SELECT DISTINCT ON (p.ingredient_id) p.ingredient_id, p.cost_per_base_unit, p.priced_at, p.owner_id
                FROM ingredient_prices p
                WHERE p.ingredient_id IN (:ids) AND (p.owner_id = :tenant OR p.owner_id IS NULL)
                ORDER BY p.ingredient_id, (p.owner_id IS NULL), p.priced_at DESC, p.recorded_at DESC""",
                new MapSqlParameterSource().addValue("ids", Set.copyOf(ingredientIds)).addValue("tenant", tenantId), rs -> {
                    UUID id = rs.getObject("ingredient_id", UUID.class);
                    prices.put(id, new EffectivePrice(id, rs.getBigDecimal("cost_per_base_unit"), rs.getObject("priced_at", LocalDate.class),
                            rs.getObject("owner_id") == null ? PriceSource.REFERENCE : PriceSource.OWN));
                });
        ingredientIds.forEach(id -> prices.putIfAbsent(id, EffectivePrice.none(id)));
        return prices;
    }

    /** Engine view of ingredients (dimension, density, effective cost) in one round trip each. */
    public Map<UUID, CostIngredient> costIngredients(UUID tenantId, Collection<UUID> ingredientIds) {
        if (ingredientIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, EffectivePrice> prices = find(tenantId, ingredientIds);
        record Head(UUID id, Dimension dimension, BigDecimal density) {
        }
        return jdbc.query("SELECT id, base_dimension, density_g_per_ml FROM ingredients WHERE id IN (:ids)",
                        Map.of("ids", Set.copyOf(ingredientIds)),
                        (rs, i) -> new Head(rs.getObject("id", UUID.class), Dimension.valueOf(rs.getString("base_dimension")),
                                rs.getBigDecimal("density_g_per_ml")))
                .stream()
                .collect(Collectors.toMap(Head::id, h -> {
                    EffectivePrice price = prices.get(h.id());
                    return new CostIngredient(h.id(), h.dimension(), h.density(), price.costPerBaseUnit(), price.source());
                }));
    }
}
