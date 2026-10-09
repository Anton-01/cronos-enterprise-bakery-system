package com.ninsky.cronos.kitchen.fixedcost;

import com.ninsky.cronos.iam.shared.AccountCreated;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** New accounts start with the default fixed costs, in the creating transaction (baking-studio §4.6). */
@Component
@RequiredArgsConstructor
public class KitchenAccountListener {

    private final FixedCostSeeder seeder;

    @PersistenceContext
    private EntityManager entityManager;

    @EventListener
    public void on(AccountCreated event) {
        // Accounts created through JPA are not inserted until flush; the seeding ledger references users(id).
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            entityManager.flush();
        }
        seeder.ensureSeeded(event.userId());
    }
}
