package com.ninsky.cronos.kitchen.fixedcost;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Inserts {@link FixedCostSeeds} into {@code user_fixed_costs}; also used by the V22 backfill migration. */
@Repository
@RequiredArgsConstructor
public class FixedCostSeedCustomRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public boolean hasAny(UUID userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM user_fixed_costs WHERE user_id = :user)",
                Map.of("user", userId), Boolean.class));
    }

    /**
     * Inserts the seeds whose {@code seed_code} the user does not have (never renames or reactivates an existing row).
     *
     * @param active   new rows' {@code is_active}
     * @param keepAmounts false = amounts are zeroed (the user's currency is not the seeds' and there is no exchange rate)
     * @return rows inserted
     */
    public int insertMissing(UUID userId, List<FixedCostSeeds.Seed> seeds, boolean active, boolean keepAmounts, String actor) {
        int[] counts = jdbc.batchUpdate("""
                INSERT INTO user_fixed_costs (id, user_id, name, description, type, default_amount, percentage, calculation_method,
                    is_active, version, created_at, updated_at, created_by, updated_by, applies_by_default, monthly_amount, monthly_basis,
                    seed_code)
                VALUES (:id, :user, :name, :description, :type, :amount, :percentage, :method, :active, 0, LOCALTIMESTAMP, LOCALTIMESTAMP,
                    :actor, :actor, :appliesByDefault, :monthlyAmount, :monthlyBasis, :code)
                ON CONFLICT (user_id, seed_code) DO NOTHING""",
                seeds.stream().map(seed -> params(userId, seed, active, keepAmounts, actor)).toArray(SqlParameterSource[]::new));
        return Arrays.stream(counts).map(count -> Math.max(count, 0)).sum();
    }

    private static MapSqlParameterSource params(UUID userId, FixedCostSeeds.Seed seed, boolean active, boolean keepAmounts, String actor) {
        return new MapSqlParameterSource().addValue("id", UUID.randomUUID()).addValue("user", userId).addValue("name", seed.name())
                .addValue("description", FixedCostSeeds.DESCRIPTION).addValue("type", seed.type())
                .addValue("amount", keepAmounts ? seed.defaultAmount() : BigDecimal.ZERO).addValue("percentage", seed.percentage())
                .addValue("method", seed.method().name()).addValue("active", active)
                .addValue("appliesByDefault", active && seed.appliesByDefault())
                .addValue("monthlyAmount", keepAmounts ? seed.monthlyAmount() : null)
                .addValue("monthlyBasis", keepAmounts ? seed.monthlyBasis() : null)
                .addValue("code", seed.code()).addValue("actor", actor);
    }
}
