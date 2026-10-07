package com.ninsky.cronos.infrastructure.persistence.core.adapter;

import com.ninsky.cronos.domain.port.core.MeasurementUnitUsagePort;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Plain JDBC on purpose: one round trip over every unit-referencing table, no entity hydration. Soft-deleted raw
 * materials still count — their historical costs were computed with the unit too.
 * {@code recipe_ingredients.unit_id} has no foreign key (V1), which is exactly why this check has
 * to exist in the application rather than relying on an FK violation.
 */
@Repository
public class MeasurementUnitUsageCustomRepository implements MeasurementUnitUsagePort {

    private static final String REFERENCED_UNIT_IDS = """
            SELECT purchase_unit_id AS unit_id FROM raw_materials WHERE purchase_unit_id IN (:ids)
            UNION SELECT unit_id FROM recipe_ingredients WHERE unit_id IN (:ids)
            UNION SELECT purchase_unit_id FROM ingredient_prices WHERE purchase_unit_id IN (:ids)
            UNION SELECT unit_id FROM recipe_lines WHERE unit_id IN (:ids)
            UNION SELECT volume_unit_id FROM ingredient_conversions WHERE volume_unit_id IN (:ids)
            UNION SELECT mass_unit_id FROM ingredient_conversions WHERE mass_unit_id IN (:ids)
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public MeasurementUnitUsageCustomRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Set<Long> findReferencedUnitIds(Collection<Long> unitIds) {
        if (unitIds == null || unitIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jdbcTemplate.queryForList(REFERENCED_UNIT_IDS, Map.of("ids", unitIds), Long.class));
    }
}
