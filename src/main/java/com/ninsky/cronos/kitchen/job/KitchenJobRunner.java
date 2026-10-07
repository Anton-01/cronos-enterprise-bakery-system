package com.ninsky.cronos.kitchen.job;

import com.ninsky.cronos.kitchen.recipe.QuoteFlagCustomRepository;
import com.ninsky.cronos.kitchen.recipe.RecipeCosting;
import com.ninsky.cronos.kitchen.recipe.RecipeRevisions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** Drains {@code kitchen_jobs}: oversized ripples and reference-price changes, one job per transaction. */
@Slf4j
@Component
@RequiredArgsConstructor
public class KitchenJobRunner {

    private final KitchenJobs jobs;
    private final RecipeCosting costing;
    private final QuoteFlagCustomRepository quoteFlags;
    private final TransactionTemplate transactions;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${app.kitchen.job-delay-ms:15000}", initialDelayString = "${app.kitchen.job-initial-delay-ms:20000}")
    public void drain() {
        while (runOne()) {
            // next job
        }
    }

    /** Claims, processes and completes one job in one transaction; a failure is recorded in a fresh one. */
    private boolean runOne() {
        AtomicReference<KitchenJobs.Claimed> current = new AtomicReference<>();
        try {
            return Boolean.TRUE.equals(transactions.execute(status -> {
                Optional<KitchenJobs.Claimed> claimed = jobs.claim();
                claimed.ifPresent(job -> {
                    current.set(job);
                    process(job.job());
                    jobs.done(job.id());
                });
                return claimed.isPresent();
            }));
        } catch (RuntimeException failure) {
            KitchenJobs.Claimed job = current.get();
            if (job == null) {
                throw failure;
            }
            log.warn("Kitchen job {} failed (attempt {}): {}", job.id(), job.attempts() + 1, failure.getMessage());
            transactions.executeWithoutResult(status -> jobs.failed(job.id(), job.attempts() + 1, failure.getMessage()));
            return true;
        }
    }

    private void process(RecalculationJob job) {
        List<UUID> ids = job.recipeIds() != null ? job.recipeIds() : jobs.recipesWithoutOwnPrice(job.ingredientId());
        RecipeRevisions.Reason reason = new RecipeRevisions.Reason(job.reasonKey(), job.reasonArgs() == null ? List.of() : job.reasonArgs());
        costing.recalculate(ids, job.actor(), clock.instant(), reason);
        quoteFlags.flagOpenQuotes(null, ids);
    }
}
