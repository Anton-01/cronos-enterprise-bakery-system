package com.ninsky.cronos.kitchen.fixedcost;

import com.ninsky.cronos.eventing.application.EventEnvelope;
import com.ninsky.cronos.eventing.application.OutboxHandler;
import com.ninsky.cronos.identity.domain.IdentityEvents;
import org.springframework.stereotype.Component;

/**
 * Seeds the default fixed costs of a new account after it committed (outbox consumer), off the sign-up request. Kitchen
 * reads still seed lazily, so a screen opened before this runs never misses them; seeding is idempotent.
 */
@Component
public class KitchenAccountListener implements OutboxHandler<IdentityEvents.AccountCreated> {

    private final FixedCostSeeder seeder;

    public KitchenAccountListener(FixedCostSeeder seeder) {
        this.seeder = seeder;
    }

    @Override
    public Class<IdentityEvents.AccountCreated> eventClass() {
        return IdentityEvents.AccountCreated.class;
    }

    @Override
    public void handle(IdentityEvents.AccountCreated event, EventEnvelope envelope) {
        seeder.ensureSeeded(event.userId());
    }

    @Override
    public String name() {
        return "KitchenFixedCostSeeding";
    }
}
