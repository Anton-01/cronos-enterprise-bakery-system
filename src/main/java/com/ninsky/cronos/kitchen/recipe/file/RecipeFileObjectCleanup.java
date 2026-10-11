package com.ninsky.cronos.kitchen.recipe.file;

import com.ninsky.cronos.eventing.application.EventEnvelope;
import com.ninsky.cronos.eventing.application.OutboxHandler;
import com.ninsky.cronos.infrastructure.storage.StoragePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Deletes released recipe file objects (outbox consumer). Deleting a missing object is a no-op, so a retry after a
 * partial failure is harmless; a storage outage retries instead of leaving orphans.
 */
@Slf4j
@Component
public class RecipeFileObjectCleanup implements OutboxHandler<RecipeFilesReleased> {

    private final StoragePort storage;

    public RecipeFileObjectCleanup(StoragePort storage) {
        this.storage = storage;
    }

    @Override
    public Class<RecipeFilesReleased> eventClass() {
        return RecipeFilesReleased.class;
    }

    @Override
    public void handle(RecipeFilesReleased event, EventEnvelope envelope) {
        event.keys().forEach(storage::deleteFile);
        log.debug("Deleted {} released recipe file object(s)", event.keys().size());
    }

    @Override
    public String name() {
        return "RecipeFileObjectCleanup";
    }
}
