package com.ninsky.cronos.kitchen.recipe.file;

import com.ninsky.cronos.kitchen.recipe.RecipeQueryCustomRepository;
import com.ninsky.cronos.kitchen.shared.KitchenProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

/** Purges files of recipes soft-deleted more than {@code filePurgeAfterDays} ago (§5.2). */
@Component
@RequiredArgsConstructor
public class RecipeFilePurgeJob {

    private final RecipeQueryCustomRepository recipes;
    private final RecipeFileCustomRepository files;
    private final RecipeFileService fileService;
    private final KitchenProperties properties;
    private final TransactionTemplate transactions;
    private final Clock clock;

    @Scheduled(cron = "${app.kitchen.file-purge-cron:0 15 4 * * *}", zone = "America/Mexico_City")
    public void purge() {
        for (UUID recipeId : recipes.deletedBefore(clock.instant().minus(Duration.ofDays(properties.filePurgeAfterDays())))) {
            transactions.executeWithoutResult(status -> files.list(recipeId).forEach(file -> {
                files.delete(file.id());
                fileService.deleteBlobsAfterCommit(file.storageKey(), file.thumbnailKey());
            }));
        }
    }
}
