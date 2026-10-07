package com.ninsky.cronos.kitchen.costing;

import com.ninsky.cronos.kitchen.shared.Dimension;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Effective-price SQL (§2): own price first, then the platform reference. */
@Repository
@RequiredArgsConstructor
public class IngredientPriceCustomRepository {

    /** Effective price per ingredient {@code i} for {@code :tenant}, joined as {@code ep}. */
    public static final String LATERAL = """
            LEFT JOIN LATERAL (SELECT p.cost_per_base_unit, p.priced_at, p.owner_id FROM ingredient_prices p
                WHERE p.ingredient_id = i.id AND (p.owner_id = :tenant OR p.owner_id IS NULL)
                ORDER BY (p.owner_id IS NULL), p.priced_at DESC, p.recorded_at DESC LIMIT 1) ep ON TRUE""";

    private final NamedParameterJdbcTemplate jdbc;

    /** Engine-relevant ingredient columns. */
    public record CostHead(UUID id, Dimension dimension, BigDecimal density) {
    }

    /** One {@code DISTINCT ON} query; ingredients without any price are absent. */
    public List<EffectivePrice> effective(UUID tenantId, Collection<UUID> ingredientIds) {
        return jdbc.query("""
                SELECT DISTINCT ON (p.ingredient_id) p.ingredient_id, p.cost_per_base_unit, p.priced_at, p.owner_id
                FROM ingredient_prices p
                WHERE p.ingredient_id IN (:ids) AND (p.owner_id = :tenant OR p.owner_id IS NULL)
                ORDER BY p.ingredient_id, (p.owner_id IS NULL), p.priced_at DESC, p.recorded_at DESC""",
                new MapSqlParameterSource().addValue("ids", Set.copyOf(ingredientIds)).addValue("tenant", tenantId),
                (rs, i) -> new EffectivePrice(rs.getObject("ingredient_id", UUID.class), rs.getBigDecimal("cost_per_base_unit"),
                        rs.getObject("priced_at", LocalDate.class), rs.getObject("owner_id") == null ? PriceSource.REFERENCE : PriceSource.OWN));
    }

    public List<CostHead> costHeads(Collection<UUID> ingredientIds) {
        return jdbc.query("SELECT id, base_dimension, density_g_per_ml FROM ingredients WHERE id IN (:ids)",
                Map.of("ids", Set.copyOf(ingredientIds)),
                (rs, i) -> new CostHead(rs.getObject("id", UUID.class), Dimension.valueOf(rs.getString("base_dimension")),
                        rs.getBigDecimal("density_g_per_ml")));
    }
}
