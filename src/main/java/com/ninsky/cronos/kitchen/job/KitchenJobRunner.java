package com.ninsky.cronos.kitchen.job;

import com.ninsky.cronos.kitchen.recipe.QuoteFlags;
import com.ninsky.cronos.kitchen.recipe.RecipeCosting;
import com.ninsky.cronos.kitchen.recipe.RecipeRevisions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
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
    private final QuoteFlags quoteFlags;
    private final NamedParameterJdbcTemplate jdbc;
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
        List<UUID> ids = job.recipeIds() != null ? job.recipeIds() : recipesWithoutOwnPrice(job.ingredientId());
        RecipeRevisions.Reason reason = new RecipeRevisions.Reason(job.reasonKey(), job.reasonArgs() == null ? List.of() : job.reasonArgs());
        costing.recalculate(ids, job.actor(), clock.instant(), reason);
        quoteFlags.flagOpenQuotes(null, ids);
    }

    private List<UUID> recipesWithoutOwnPrice(UUID ingredientId) {
        return jdbc.queryForList("""
                SELECT DISTINCT r.id FROM recipe_lines l JOIN recipes r ON r.id = l.recipe_id
                WHERE l.ingredient_id = :ingredient AND r.deleted_at IS NULL
                  AND NOT EXISTS (SELECT 1 FROM ingredient_prices p WHERE p.ingredient_id = :ingredient AND p.owner_id = r.owner_id)""",
                new MapSqlParameterSource().addValue("ingredient", ingredientId), UUID.class);
    }
}
