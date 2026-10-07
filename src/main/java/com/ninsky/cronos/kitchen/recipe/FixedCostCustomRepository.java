package com.ninsky.cronos.kitchen.recipe;

import com.ninsky.cronos.kitchen.costing.FixedCostMethod;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The tenant's master fixed costs ({@code user_fixed_costs}). */
@Repository
@RequiredArgsConstructor
public class FixedCostCustomRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public record Master(UUID id, String name, FixedCostMethod method, BigDecimal defaultAmount, BigDecimal percentage, boolean active) {
    }

    public Map<UUID, Master> find(UUID tenant, Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return jdbc.query("""
                        SELECT id, name, calculation_method, default_amount, percentage, coalesce(is_active, TRUE) AS active
                        FROM user_fixed_costs WHERE user_id = :tenant AND id IN (:ids)""",
                        new MapSqlParameterSource().addValue("tenant", tenant).addValue("ids", Set.copyOf(ids)),
                        (rs, i) -> new Master(rs.getObject("id", UUID.class), rs.getString("name"),
                                FixedCostMethod.parse(rs.getString("calculation_method")).orElse(FixedCostMethod.FIXED_PER_BATCH),
                                rs.getBigDecimal("default_amount"), rs.getBigDecimal("percentage"), rs.getBoolean("active")))
                .stream().collect(Collectors.toMap(Master::id, Function.identity()));
    }
}
