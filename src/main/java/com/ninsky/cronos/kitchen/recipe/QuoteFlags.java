package com.ninsky.cronos.kitchen.recipe;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Quote side effects of recipe changes (§4.5 step 3) and the margin reference price. */
@Component
@RequiredArgsConstructor
public class QuoteFlags {

    private final NamedParameterJdbcTemplate jdbc;

    /** Flags open (DRAFT, SENT) quotes whose lines use the recipes; returns the number of quotes flagged. */
    public int flagOpenQuotes(UUID tenantId, Collection<UUID> recipeIds) {
        if (recipeIds.isEmpty()) {
            return 0;
        }
        Integer flagged = jdbc.queryForObject("""
                WITH items AS (
                    UPDATE quote_items qi SET price_review_required = TRUE
                    FROM quotes q
                    WHERE q.id = qi.quote_id AND q.status IN ('DRAFT', 'SENT') AND qi.recipe_id IN (:recipes)
                      AND (CAST(:tenant AS uuid) IS NULL OR q.user_id = :tenant)
                    RETURNING qi.quote_id),
                quotes_flagged AS (
                    UPDATE quotes SET price_review_required = TRUE WHERE id IN (SELECT quote_id FROM items) RETURNING id)
                SELECT count(*) FROM quotes_flagged""",
                new MapSqlParameterSource().addValue("recipes", Set.copyOf(recipeIds)).addValue("tenant", tenantId), Integer.class);
        return flagged == null ? 0 : flagged;
    }

    /** Unit price of the most recent ACCEPTED quote line per recipe. */
    public Map<UUID, BigDecimal> acceptedPrices(UUID tenantId, Collection<UUID> recipeIds) {
        Map<UUID, BigDecimal> prices = new HashMap<>();
        if (recipeIds.isEmpty()) {
            return prices;
        }
        jdbc.query("""
                SELECT DISTINCT ON (qi.recipe_id) qi.recipe_id, qi.unit_price FROM quote_items qi JOIN quotes q ON q.id = qi.quote_id
                WHERE q.status = 'ACCEPTED' AND qi.recipe_id IN (:recipes) AND (CAST(:tenant AS uuid) IS NULL OR q.user_id = :tenant)
                ORDER BY qi.recipe_id, coalesce(q.updated_at, q.created_at) DESC""",
                new MapSqlParameterSource().addValue("recipes", Set.copyOf(recipeIds)).addValue("tenant", tenantId),
                rs -> {
                    prices.put(rs.getObject("recipe_id", UUID.class), rs.getBigDecimal("unit_price"));
                });
        return prices;
    }
}
