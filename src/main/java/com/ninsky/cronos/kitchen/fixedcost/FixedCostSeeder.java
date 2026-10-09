package com.ninsky.cronos.kitchen.fixedcost;

import com.ninsky.cronos.kitchen.costing.CostContext;
import com.ninsky.cronos.kitchen.shared.KitchenSettingsCustomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;

/**
 * Per-user default fixed costs (baking-studio §4.6). Seeding happens once per user (B4): on account creation,
 * or lazily on the first {@code GET /user-fixed-cost} for accounts created before this existed. A user who
 * already keeps fixed costs receives the seeds inactive, so their current costing does not change.
 * Callers own the transaction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FixedCostSeeder {

    static final String ACTOR = "SYSTEM_SEED";

    private final KitchenSettingsCustomRepository settings;
    private final FixedCostSeedCustomRepository seeds;
    private final CostContext costContext;
    private final Clock clock;

    /** Seeds once; concurrent first requests serialise on the settings row. */
    public void ensureSeeded(UUID userId) {
        if (settings.peek(userId).fixedCosts() || settings.lock(userId).fixedCosts()) {
            return;
        }
        int inserted = insert(userId, !seeds.hasAny(userId));
        settings.markFixedCostsSeeded(userId, clock.instant());
        log.info("Seeded {} default fixed costs for user {}", inserted, userId);
    }

    /** Explicit "restore defaults": re-creates any default whose seed code the user lacks; never renames or deletes. */
    public int restoreDefaults(UUID userId) {
        settings.lock(userId);
        int inserted = insert(userId, true);
        settings.markFixedCostsSeeded(userId, clock.instant());
        return inserted;
    }

    /** Amounts are MXN references: in another currency (no exchange rate) they arrive inactive and zeroed. */
    private int insert(UUID userId, boolean activeIfPossible) {
        boolean sameCurrency = FixedCostSeeds.CURRENCY.equalsIgnoreCase(costContext.current().currency());
        return seeds.insertMissing(userId, FixedCostSeeds.ALL, activeIfPossible && sameCurrency, sameCurrency, ACTOR);
    }
}
