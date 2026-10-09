package com.ninsky.cronos.kitchen.fixedcost;

import com.ninsky.cronos.kitchen.costing.CostContext;
import com.ninsky.cronos.kitchen.costing.CostEngine;
import com.ninsky.cronos.kitchen.shared.KitchenSettingsCustomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.RoundingMode;

import static com.ninsky.cronos.finance.FinanceTestData.ACTOR_ID;
import static com.ninsky.cronos.finance.FinanceTestData.CLOCK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FixedCostSeederTest {

    @Mock
    private KitchenSettingsCustomRepository settings;
    @Mock
    private FixedCostSeedCustomRepository seeds;
    @Mock
    private CostContext costContext;

    private FixedCostSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = new FixedCostSeeder(settings, seeds, costContext, CLOCK);
        currency("MXN");
        when(settings.peek(ACTOR_ID)).thenReturn(new KitchenSettingsCustomRepository.Seeded(false, false));
        when(settings.lock(ACTOR_ID)).thenReturn(new KitchenSettingsCustomRepository.Seeded(false, false));
    }

    private void currency(String code) {
        when(costContext.current()).thenReturn(new CostContext.Money(code, new CostEngine.Rules(2, RoundingMode.HALF_UP)));
    }

    @Test
    void newUserGetsActiveSeedsWithAmounts() {
        when(seeds.hasAny(ACTOR_ID)).thenReturn(false);

        seeder.ensureSeeded(ACTOR_ID);

        verify(seeds).insertMissing(ACTOR_ID, FixedCostSeeds.ALL, true, true, FixedCostSeeder.ACTOR);
        verify(settings).markFixedCostsSeeded(eq(ACTOR_ID), any());
    }

    @Test
    void userWithOwnCostsGetsThemInactiveSoNoPriceMoves() {
        when(seeds.hasAny(ACTOR_ID)).thenReturn(true);

        seeder.ensureSeeded(ACTOR_ID);

        verify(seeds).insertMissing(ACTOR_ID, FixedCostSeeds.ALL, false, true, FixedCostSeeder.ACTOR);
    }

    @Test
    void anotherCurrencyGetsInactiveZeroedSeeds() {
        currency("USD");

        seeder.ensureSeeded(ACTOR_ID);

        verify(seeds).insertMissing(ACTOR_ID, FixedCostSeeds.ALL, false, false, FixedCostSeeder.ACTOR);
    }

    @Test
    void seedsOnlyOnceNeverResurrectingDeletions() {
        when(settings.peek(ACTOR_ID)).thenReturn(new KitchenSettingsCustomRepository.Seeded(false, true));

        seeder.ensureSeeded(ACTOR_ID);

        verify(settings, never()).lock(any());
        verify(seeds, never()).insertMissing(any(), any(), anyBoolean(), anyBoolean(), any());
    }

    @Test
    void concurrentFirstRequestRechecksUnderTheLock() {
        when(settings.lock(ACTOR_ID)).thenReturn(new KitchenSettingsCustomRepository.Seeded(false, true));

        seeder.ensureSeeded(ACTOR_ID);

        verify(seeds, never()).insertMissing(any(), any(), anyBoolean(), anyBoolean(), any());
    }

    @Test
    void catalogMatchesTheContract() {
        assertThat(FixedCostSeeds.ALL).hasSize(14);
        assertThat(FixedCostSeeds.ALL).filteredOn(FixedCostSeeds.Seed::appliesByDefault).extracting(FixedCostSeeds.Seed::code)
                .containsExactly("LABOR_BAKER");
        assertThat(FixedCostSeeds.ALL).allSatisfy(seed -> assertThat(seed.monthlyAmount() == null).isEqualTo(seed.monthlyBasis() == null));
    }
}
