package com.ninsky.cronos.kitchen.recipe;

import com.ninsky.cronos.infrastructure.web.paging.CatalogPage;
import com.ninsky.cronos.infrastructure.web.paging.PageQuery;
import com.ninsky.cronos.kitchen.shared.Scope;
import com.ninsky.cronos.kitchen.shared.Sql;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Read side of recipes: filtered pages, header stats, quote picker, contained allergens per page. */
@Repository
@RequiredArgsConstructor
public class RecipeQueryCustomRepository {

    static final Map<String, String> SORTS = Map.of(
            "updatedAt", "coalesce(r.updated_at, r.created_at)",
            "name", "kitchen_fold(r.name)",
            "costPerUnit", "r.cost_per_unit");
    static final String DEFAULT_SORT = "updatedAt,desc";

    private static final String FREE_OF = """
             AND NOT EXISTS (SELECT 1 FROM recipe_lines l WHERE l.recipe_id = r.id AND NOT l.optional AND (
                EXISTS (SELECT 1 FROM ingredient_allergens ia WHERE ia.ingredient_id = l.ingredient_id AND ia.allergen_id IN (:freeOf))
                OR EXISTS (SELECT 1 FROM recipe_line_allergens la WHERE la.line_id = l.id AND la.allergen_id IN (:freeOf))))""";

    private final NamedParameterJdbcTemplate jdbc;
    private final RecipeCustomRepository store;

    /** Page of recipe ids in order; heads are loaded by the store. */
    public CatalogPage<UUID> page(UUID tenant, RecipeFilter filter, PageQuery query) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("tenant", tenant)
                .addValue("limit", query.size()).addValue("offset", query.offset());
        StringBuilder where = new StringBuilder(" WHERE r.deleted_at IS NULL");
        where.append(filter.scope() == null ? " AND (r.owner_id IS NULL OR r.owner_id = :tenant)"
                : filter.scope() == Scope.SYSTEM ? " AND r.owner_id IS NULL" : " AND r.owner_id = :tenant");
        Optional.ofNullable(Sql.likeFolded(filter.search())).ifPresent(q -> {
            params.addValue("q", q);
            where.append(" AND (kitchen_fold(r.name) LIKE :q OR kitchen_fold(r.code) LIKE :q)");
        });
        if (!filter.categoryIds().isEmpty()) {
            params.addValue("categories", Set.copyOf(filter.categoryIds()));
            where.append(" AND r.category_id IN (:categories)");
        }
        if (!filter.statuses().isEmpty()) {
            params.addValue("statuses", filter.statuses().stream().map(Enum::name).collect(java.util.stream.Collectors.toSet()));
            where.append(" AND r.status IN (:statuses)");
        }
        if (!filter.freeOfAllergenIds().isEmpty()) {
            params.addValue("freeOf", Set.copyOf(filter.freeOfAllergenIds()));
            where.append(FREE_OF);
        }
        if (filter.costStatus() != null) {
            params.addValue("costStatus", filter.costStatus().name());
            where.append(" AND r.cost_status = :costStatus");
        }
        Long total = jdbc.queryForObject("SELECT count(*) FROM recipes r" + where, params, Long.class);
        List<UUID> ids = jdbc.queryForList("SELECT r.id FROM recipes r" + where + " ORDER BY " + query.orderBySql()
                + ", r.id LIMIT :limit OFFSET :offset", params, UUID.class);
        return CatalogPage.of(ids, query, total == null ? 0 : total);
    }

    /** Contained allergen ids (required lines; ingredient-declared ∪ line extras) per recipe. */
    public Map<UUID, Set<Long>> containedAllergens(Collection<UUID> recipeIds) {
        Map<UUID, Set<Long>> result = new HashMap<>();
        if (recipeIds.isEmpty()) {
            return result;
        }
        jdbc.query("""
                SELECT l.recipe_id, ia.allergen_id FROM recipe_lines l JOIN ingredient_allergens ia ON ia.ingredient_id = l.ingredient_id
                WHERE l.recipe_id IN (:ids) AND NOT l.optional
                UNION
                SELECT l.recipe_id, la.allergen_id FROM recipe_lines l JOIN recipe_line_allergens la ON la.line_id = l.id
                WHERE l.recipe_id IN (:ids) AND NOT l.optional""",
                Map.of("ids", Set.copyOf(recipeIds)), rs -> {
                    result.computeIfAbsent(rs.getObject("recipe_id", UUID.class), k -> new LinkedHashSet<>()).add(rs.getLong("allergen_id"));
                });
        return result;
    }

    /** Heads (no lines) for list rows, in the given order. */
    public List<RecipeAggregate.Head> heads(List<UUID> ids) {
        return store.headsInOrder(ids);
    }

    public RecipeStats stats(UUID tenant) {
        return jdbc.queryForObject("""
                SELECT count(*) AS total, count(*) FILTER (WHERE r.status = 'ACTIVE') AS active,
                       count(*) FILTER (WHERE r.status = 'DRAFT') AS drafts,
                       count(*) FILTER (WHERE r.cost_status <> 'CURRENT') AS stale,
                       count(*) FILTER (WHERE r.cost_per_unit > 0 AND ref.price IS NOT NULL
                           AND (ref.price - r.cost_per_unit) * 100 / r.cost_per_unit < r.target_margin_percent) AS below
                FROM recipes r
                LEFT JOIN LATERAL (SELECT qi.unit_price AS price FROM quote_items qi JOIN quotes q ON q.id = qi.quote_id
                    WHERE qi.recipe_id = r.id AND q.status = 'ACCEPTED' AND q.user_id = :tenant
                    ORDER BY coalesce(q.updated_at, q.created_at) DESC LIMIT 1) ref ON TRUE
                WHERE r.owner_id = :tenant AND r.deleted_at IS NULL""",
                Map.of("tenant", tenant),
                (rs, i) -> new RecipeStats(rs.getLong("total"), rs.getLong("active"), rs.getLong("drafts"), rs.getLong("stale"),
                        rs.getLong("below")));
    }

    public List<RecipeOption> simple(UUID tenant, String search) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("tenant", tenant).addValue("q", Sql.likeFolded(search));
        return jdbc.query("""
                SELECT r.id, r.name, r.description, r.total_cost, r.cost_per_unit, r.yield_unit FROM recipes r
                WHERE r.owner_id = :tenant AND r.deleted_at IS NULL AND r.status = 'ACTIVE'
                  AND (CAST(:q AS text) IS NULL OR kitchen_fold(r.name) LIKE :q)
                ORDER BY kitchen_fold(r.name) LIMIT 50""", params,
                (rs, i) -> new RecipeOption(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("description"),
                        rs.getBigDecimal("total_cost"), rs.getBigDecimal("cost_per_unit"), rs.getString("yield_unit")));
    }

    /** Soft-deleted recipes with files, deleted before {@code cutoff} (file purge). */
    public List<UUID> deletedBefore(java.time.Instant cutoff) {
        return jdbc.queryForList("""
                SELECT DISTINCT r.id FROM recipes r JOIN recipe_files f ON f.recipe_id = r.id
                WHERE r.deleted_at IS NOT NULL AND r.deleted_at < :cutoff""",
                Map.of("cutoff", cutoff.atOffset(java.time.ZoneOffset.UTC)), UUID.class);
    }
}
