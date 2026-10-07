package com.ninsky.cronos.kitchen.ingredient;

import com.ninsky.cronos.finance.shared.UserRef;
import com.ninsky.cronos.finance.shared.UserRefCustomRepository;
import com.ninsky.cronos.infrastructure.web.paging.CatalogPage;
import com.ninsky.cronos.infrastructure.web.paging.PageQuery;
import com.ninsky.cronos.kitchen.costing.IngredientPriceCustomRepository;
import com.ninsky.cronos.kitchen.costing.PriceSource;
import com.ninsky.cronos.kitchen.shared.Dimension;
import com.ninsky.cronos.kitchen.shared.KitchenStatus;
import com.ninsky.cronos.kitchen.shared.Scope;
import com.ninsky.cronos.kitchen.shared.Sql;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Read side of ingredients: one SQL per list/detail with the effective price joined LATERAL. */
@Repository
@RequiredArgsConstructor
public class IngredientQueryCustomRepository {

    static final Map<String, String> SORTS = Map.of(
            "name", "kitchen_fold(n.name)",
            "costPerBaseUnit", "ep.cost_per_base_unit",
            "pricedAt", "ep.priced_at",
            "usedInRecipes", "used");
    static final String DEFAULT_SORT = "name,asc";
    static final Map<String, String> HISTORY_SORTS = Map.of("pricedAt", "p.priced_at");

    private static final String USED = """
            LEFT JOIN LATERAL (SELECT count(DISTINCT l.recipe_id) AS used FROM recipe_lines l JOIN recipes r ON r.id = l.recipe_id
                WHERE l.ingredient_id = i.id AND r.owner_id = :tenant AND r.deleted_at IS NULL) u ON TRUE""";
    private static final String FROM = " FROM ingredients i JOIN ingredient_i18n n ON n.ingredient_id = i.id AND n.locale = :lang"
            + " JOIN categories c ON c.id = i.category_id " + IngredientPriceCustomRepository.LATERAL + " " + USED;
    private static final String VISIBLE = " WHERE (i.owner_id IS NULL OR i.owner_id = :tenant)";
    private static final String COLUMNS = "SELECT i.id, i.code, i.owner_id, i.category_id, c.name AS category_name, i.base_dimension, "
            + "i.yield_percent, i.status, n.name, ep.cost_per_base_unit, ep.priced_at, ep.owner_id AS price_owner, coalesce(u.used, 0) AS used";

    private final NamedParameterJdbcTemplate jdbc;
    private final UserRefCustomRepository userRefs;

    /** A summary row before allergens and unit codes are attached. */
    public record Row(UUID id, String code, UUID ownerId, Long categoryId, String categoryName, Dimension baseDimension,
                      BigDecimal yieldPercent, KitchenStatus status, String name, BigDecimal costPerBaseUnit, LocalDate pricedAt,
                      PriceSource priceSource, long usedInRecipes) {

        public Scope scope() {
            return Scope.of(ownerId);
        }
    }

    /** Extra detail columns. */
    public record Extra(String description, String brand, BigDecimal densityGPerMl, Instant createdAt, Instant updatedAt, UserRef updatedBy,
                        long version) {
    }

    public CatalogPage<Row> page(UUID tenant, String language, IngredientFilter filter, LocalDate staleBefore, PageQuery query) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("tenant", tenant).addValue("lang", language)
                .addValue("staleBefore", staleBefore).addValue("limit", query.size()).addValue("offset", query.offset());
        StringBuilder where = new StringBuilder(VISIBLE);
        Optional.ofNullable(Sql.likeFolded(filter.search())).ifPresent(q -> {
            params.addValue("q", q);
            where.append(" AND (kitchen_fold(n.name) LIKE :q OR kitchen_fold(i.code) LIKE :q OR kitchen_fold(coalesce(i.brand, '')) LIKE :q")
                    .append(" OR kitchen_fold(c.name) LIKE :q)");
        });
        if (!filter.categoryIds().isEmpty()) {
            params.addValue("categories", Set.copyOf(filter.categoryIds()));
            where.append(" AND i.category_id IN (:categories)");
        }
        if (!filter.allergenIds().isEmpty()) {
            params.addValue("allergens", Set.copyOf(filter.allergenIds()));
            where.append(filter.excludeAllergens() ? " AND NOT" : " AND").append(
                    " EXISTS (SELECT 1 FROM ingredient_allergens ia WHERE ia.ingredient_id = i.id AND ia.allergen_id IN (:allergens))");
        }
        if (filter.scope() != null) {
            where.append(filter.scope() == Scope.SYSTEM ? " AND i.owner_id IS NULL" : " AND i.owner_id = :tenant");
        }
        if (filter.priceStale() != null) {
            where.append(filter.priceStale() ? " AND ep.priced_at < :staleBefore" : " AND (ep.priced_at IS NULL OR ep.priced_at >= :staleBefore)");
        }
        if (filter.status() != null) {
            params.addValue("status", filter.status().name());
            where.append(" AND i.status = :status");
        }
        Long total = jdbc.queryForObject("SELECT count(*)" + FROM + where, params, Long.class);
        List<Row> rows = jdbc.query(COLUMNS + FROM + where + " ORDER BY " + query.orderBySql() + ", i.id LIMIT :limit OFFSET :offset",
                params, (rs, i) -> row(rs));
        return CatalogPage.of(rows, query, total == null ? 0 : total);
    }

    public Optional<Row> find(UUID tenant, String language, UUID id) {
        return jdbc.query(COLUMNS + FROM + VISIBLE + " AND i.id = :id",
                new MapSqlParameterSource().addValue("tenant", tenant).addValue("lang", language).addValue("id", id),
                (rs, i) -> row(rs)).stream().findFirst();
    }

    /** Visible rows by id (pickers, substitutes, recipe lines). */
    public Map<UUID, Row> findAll(UUID tenant, String language, Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Row> rows = new HashMap<>();
        jdbc.query(COLUMNS + FROM + VISIBLE + " AND i.id IN (:ids)",
                new MapSqlParameterSource().addValue("tenant", tenant).addValue("lang", language).addValue("ids", Set.copyOf(ids)),
                rs -> {
                    Row row = row(rs);
                    rows.put(row.id(), row);
                });
        return rows;
    }

    public Extra extra(UUID id) {
        return jdbc.queryForObject("SELECT i.density_g_per_ml, i.brand, i.created_at, i.updated_at, i.version, n.description, "
                        + UserRefCustomRepository.columns("i.updated_by") + " FROM ingredients i"
                        + " LEFT JOIN ingredient_i18n n ON n.ingredient_id = i.id AND n.locale = :lang"
                        + UserRefCustomRepository.join("i.updated_by") + " WHERE i.id = :id",
                new MapSqlParameterSource().addValue("id", id).addValue("lang", com.ninsky.cronos.kitchen.shared.KitchenMessages.language()),
                (rs, i) -> new Extra(rs.getString("description"), rs.getString("brand"), rs.getBigDecimal("density_g_per_ml"),
                        Sql.instant(rs, "created_at"), Sql.instant(rs, "updated_at"), userRefs.map(rs), rs.getLong("version")));
    }

    /** Every locale's name and description of one ingredient. */
    public Map<String, String[]> texts(UUID id) {
        Map<String, String[]> texts = new HashMap<>();
        jdbc.query("SELECT locale, name, description FROM ingredient_i18n WHERE ingredient_id = :id", Map.of("id", id),
                rs -> {
                    texts.put(rs.getString("locale"), new String[]{rs.getString("name"), rs.getString("description")});
                });
        return texts;
    }

    public Map<UUID, List<Long>> allergenIds(Collection<UUID> ingredientIds) {
        Map<UUID, List<Long>> result = new HashMap<>();
        if (ingredientIds.isEmpty()) {
            return result;
        }
        jdbc.query("SELECT ingredient_id, allergen_id FROM ingredient_allergens WHERE ingredient_id IN (:ids)",
                Map.of("ids", Set.copyOf(ingredientIds)), rs -> {
                    result.computeIfAbsent(rs.getObject("ingredient_id", UUID.class), k -> new ArrayList<>()).add(rs.getLong("allergen_id"));
                });
        return result;
    }

    /** Latest reference (owner NULL) and own price; keys {@link PriceSource#REFERENCE} / {@link PriceSource#OWN}. */
    public Map<PriceSource, IngredientPrice> latestPrices(UUID tenant, UUID id) {
        Map<PriceSource, IngredientPrice> prices = new HashMap<>();
        jdbc.query("""
                SELECT DISTINCT ON (p.owner_id IS NULL) p.owner_id, p.purchase_quantity, p.purchase_unit_id, mu.code_identity, p.price,
                       p.currency, p.supplier, p.priced_at
                FROM ingredient_prices p JOIN measurement_units mu ON mu.id = p.purchase_unit_id
                WHERE p.ingredient_id = :id AND (p.owner_id IS NULL OR p.owner_id = :tenant)
                ORDER BY (p.owner_id IS NULL), p.priced_at DESC, p.recorded_at DESC""",
                new MapSqlParameterSource().addValue("id", id).addValue("tenant", tenant), rs -> {
                    prices.put(rs.getObject("owner_id") == null ? PriceSource.REFERENCE : PriceSource.OWN,
                            new IngredientPrice(rs.getBigDecimal("purchase_quantity"), rs.getLong("purchase_unit_id"),
                                    rs.getString("code_identity"), rs.getBigDecimal("price"), rs.getString("currency"),
                                    rs.getString("supplier"), rs.getObject("priced_at", LocalDate.class)));
                });
        return prices;
    }

    public CatalogPage<PriceHistoryEntry> history(UUID tenant, UUID id, PageQuery query) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("id", id).addValue("tenant", tenant)
                .addValue("limit", query.size()).addValue("offset", query.offset());
        String where = " WHERE p.ingredient_id = :id AND (p.owner_id IS NULL OR p.owner_id = :tenant)";
        Long total = jdbc.queryForObject("SELECT count(*) FROM ingredient_prices p" + where, params, Long.class);
        List<PriceHistoryEntry> content = jdbc.query("SELECT p.id, p.owner_id, p.purchase_quantity, p.purchase_unit_id, mu.code_identity, "
                        + "p.price, p.currency, p.supplier, p.priced_at, p.cost_per_base_unit, p.recorded_at, "
                        + UserRefCustomRepository.columns("p.recorded_by") + " FROM ingredient_prices p"
                        + " JOIN measurement_units mu ON mu.id = p.purchase_unit_id" + UserRefCustomRepository.join("p.recorded_by") + where
                        + " ORDER BY p.priced_at DESC, p.recorded_at DESC LIMIT :limit OFFSET :offset", params,
                (rs, i) -> new PriceHistoryEntry(rs.getObject("id", UUID.class),
                        rs.getObject("owner_id") == null ? PriceSource.REFERENCE : PriceSource.OWN, rs.getBigDecimal("purchase_quantity"),
                        rs.getLong("purchase_unit_id"), rs.getString("code_identity"), rs.getBigDecimal("price"), rs.getString("currency"),
                        rs.getString("supplier"), rs.getObject("priced_at", LocalDate.class), rs.getBigDecimal("cost_per_base_unit"),
                        Sql.instant(rs, "recorded_at"), userRefs.map(rs)));
        return CatalogPage.of(content, query, total == null ? 0 : total);
    }

    /** A declared substitute pair visible to the tenant (tenant declarations win over platform ones). */
    public record SubstituteRow(UUID substituteId, BigDecimal ratio, String notes, UUID ownerId) {
    }

    public List<SubstituteRow> substitutes(UUID tenant, UUID ingredientId) {
        Map<UUID, SubstituteRow> rows = new java.util.LinkedHashMap<>();
        jdbc.query("""
                SELECT s.substitute_id, s.ratio, s.notes, s.owner_id FROM ingredient_substitutes s JOIN ingredients si ON si.id = s.substitute_id
                WHERE s.ingredient_id = :id AND (s.owner_id IS NULL OR s.owner_id = :tenant) AND (si.owner_id IS NULL OR si.owner_id = :tenant)
                ORDER BY (s.owner_id IS NULL)""",
                new MapSqlParameterSource().addValue("id", ingredientId).addValue("tenant", tenant), rs -> {
                    SubstituteRow row = new SubstituteRow(rs.getObject("substitute_id", UUID.class), rs.getBigDecimal("ratio"),
                            rs.getString("notes"), Sql.uuid(rs, "owner_id"));
                    rows.putIfAbsent(row.substituteId(), row);
                });
        return List.copyOf(rows.values());
    }

    public IngredientStats stats(UUID tenant, LocalDate staleBefore) {
        return jdbc.queryForObject("""
                SELECT count(*) AS total, count(*) FILTER (WHERE i.owner_id IS NULL) AS system_rows,
                       count(*) FILTER (WHERE i.owner_id IS NOT NULL) AS own_rows,
                       count(*) FILTER (WHERE EXISTS (SELECT 1 FROM ingredient_allergens ia WHERE ia.ingredient_id = i.id)) AS with_allergens,
                       count(*) FILTER (WHERE ep.priced_at < :staleBefore) AS stale,
                       count(*) FILTER (WHERE ep.cost_per_base_unit IS NULL) AS unpriced
                FROM ingredients i""" + " " + IngredientPriceCustomRepository.LATERAL + VISIBLE + " AND i.status = 'ACTIVE'",
                new MapSqlParameterSource().addValue("tenant", tenant).addValue("staleBefore", staleBefore),
                (rs, i) -> new IngredientStats(rs.getLong("total"), rs.getLong("system_rows"), rs.getLong("own_rows"),
                        rs.getLong("with_allergens"), rs.getLong("stale"), rs.getLong("unpriced")));
    }

    public List<IngredientUsage> usage(UUID tenant, UUID ingredientId) {
        return jdbc.query("""
                SELECT r.id, r.name, l.quantity, mu.code_identity FROM recipe_lines l
                JOIN recipes r ON r.id = l.recipe_id JOIN measurement_units mu ON mu.id = l.unit_id
                WHERE l.ingredient_id = :id AND r.owner_id = :tenant AND r.deleted_at IS NULL
                ORDER BY kitchen_fold(r.name), l.display_order""",
                new MapSqlParameterSource().addValue("id", ingredientId).addValue("tenant", tenant),
                (rs, i) -> new IngredientUsage(rs.getObject("id", UUID.class), rs.getString("name"), rs.getBigDecimal("quantity"),
                        rs.getString("code_identity")));
    }

    public Map<UUID, BigDecimal> densities(Collection<UUID> ids) {
        Map<UUID, BigDecimal> result = new HashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        jdbc.query("SELECT id, density_g_per_ml FROM ingredients WHERE id IN (:ids)", Map.of("ids", Set.copyOf(ids)), rs -> {
            result.put(rs.getObject("id", UUID.class), rs.getBigDecimal("density_g_per_ml"));
        });
        return result;
    }

    /** Used in any recipe of any tenant (dimension lock, delete guard). */
    public boolean usedAnywhere(UUID ingredientId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM recipe_lines WHERE ingredient_id = :id)",
                Map.of("id", ingredientId), Boolean.class));
    }

    /** Folded names of visible ingredients (both locales) other than {@code excludeId}, with their scope. */
    public Optional<Scope> nameOwner(UUID tenant, String name, UUID excludeId) {
        return jdbc.query("""
                SELECT i.owner_id FROM ingredients i JOIN ingredient_i18n n ON n.ingredient_id = i.id
                WHERE (i.owner_id IS NULL OR i.owner_id = :tenant) AND kitchen_fold(n.name) = kitchen_fold(:name)
                  AND i.id IS DISTINCT FROM CAST(:exclude AS uuid)
                ORDER BY (i.owner_id IS NULL) DESC LIMIT 1""",
                new MapSqlParameterSource().addValue("tenant", tenant).addValue("name", name).addValue("exclude", excludeId),
                (rs, i) -> Scope.of(Sql.uuid(rs, "owner_id"))).stream().findFirst();
    }

    /** Code clash: per tenant for USER rows, globally among SYSTEM rows. */
    public boolean codeTaken(UUID ownerId, String code) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM ingredients WHERE code = :code AND (owner_id IS NULL OR owner_id IS NOT DISTINCT FROM CAST(:owner AS uuid)))""",
                new MapSqlParameterSource().addValue("code", code).addValue("owner", ownerId), Boolean.class));
    }

    private static Row row(ResultSet rs) throws SQLException {
        BigDecimal cost = rs.getBigDecimal("cost_per_base_unit");
        PriceSource source = cost == null ? PriceSource.NONE : rs.getObject("price_owner") == null ? PriceSource.REFERENCE : PriceSource.OWN;
        return new Row(rs.getObject("id", UUID.class), rs.getString("code"), Sql.uuid(rs, "owner_id"), rs.getLong("category_id"),
                rs.getString("category_name"), Dimension.valueOf(rs.getString("base_dimension")), rs.getBigDecimal("yield_percent"),
                KitchenStatus.valueOf(rs.getString("status")), rs.getString("name"), cost, rs.getObject("priced_at", LocalDate.class),
                source, rs.getLong("used"));
    }
}
