package com.ninsky.cronos.kitchen.fixedcost.migration;

import com.ninsky.cronos.kitchen.fixedcost.FixedCostSeedCustomRepository;
import com.ninsky.cronos.kitchen.fixedcost.FixedCostSeeds;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One-off backfill of the default fixed costs (baking-studio §4.6) for every account that predates them.
 * Accounts that already keep fixed costs receive the seeds inactive, so no current price changes; amounts
 * are only kept when the tenant's default currency is MXN (no exchange rates exist). Idempotent through
 * {@code (user_id, seed_code)} and the {@code fixed_costs_seeded_at} ledger.
 */
@Slf4j
@Component
@SuppressWarnings("java:S101") // Flyway derives the version from this class name.
public class V22__seed_default_fixed_costs extends BaseJavaMigration {

    private static final String ACTOR = "FLYWAY_V22";

    @Override
    public void migrate(Context context) {
        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(new SingleConnectionDataSource(context.getConnection(), true));
        FixedCostSeedCustomRepository seeds = new FixedCostSeedCustomRepository(jdbc);
        boolean mxn = jdbc.queryForList("SELECT code FROM currencies WHERE is_default", Map.of(), String.class).stream()
                .findFirst().map(FixedCostSeeds.CURRENCY::equalsIgnoreCase).orElse(true);
        List<UUID> pending = jdbc.queryForList("""
                SELECT u.id FROM users u LEFT JOIN user_kitchen_settings s ON s.user_id = u.id
                WHERE s.fixed_costs_seeded_at IS NULL ORDER BY u.id""", Map.of(), UUID.class);
        int rows = 0;
        for (UUID userId : pending) {
            rows += seeds.insertMissing(userId, FixedCostSeeds.ALL, mxn && !seeds.hasAny(userId), mxn, ACTOR);
            jdbc.update("""
                    INSERT INTO user_kitchen_settings (user_id, fixed_costs_seeded_at) VALUES (:user, now())
                    ON CONFLICT (user_id) DO UPDATE SET fixed_costs_seeded_at = now()""", Map.of("user", userId));
        }
        log.info("V22: default fixed costs seeded for {} accounts ({} rows)", pending.size(), rows);
    }
}
