package com.ninsky.cronos.kitchen.recipe;

import com.ninsky.cronos.kitchen.job.KitchenJobs;
import com.ninsky.cronos.kitchen.job.RecalculationJob;
import com.ninsky.cronos.kitchen.shared.KitchenProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Copies the SYSTEM recipe library into the demo tenant ({@code app.kitchen.demo-tenant} = username,
 * §10.5) once; copies already present (same code or name) are skipped, so restarts are no-ops.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DemoLibraryInstaller implements ApplicationRunner {

    private final KitchenProperties properties;
    private final RecipeLibraryCustomRepository library;
    private final KitchenJobs jobs;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Optional.ofNullable(properties.demoTenant()).filter(s -> !s.isBlank()).flatMap(library::userIdByUsername).ifPresent(this::install);
    }

    private void install(UUID tenant) {
        List<UUID> ids = library.copyRecipes(tenant);
        if (ids.isEmpty()) {
            return;
        }
        library.copyLines(ids);
        jobs.enqueue(new RecalculationJob(null, ids, "kitchen.revision.seeded", List.of(), tenant));
        log.info("Installed {} library recipes for the demo tenant", ids.size());
    }
}
