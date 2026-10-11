package com.ninsky.cronos.kitchen.recipe.file;

import com.ninsky.cronos.eventing.domain.DomainEvent;
import com.ninsky.cronos.eventing.domain.EventType;

import java.util.List;
import java.util.UUID;

/** Storage objects of deleted recipe files (originals and thumbnails), deleted after the deleting transaction commits. */
@EventType("kitchen.recipe-files-released")
public record RecipeFilesReleased(UUID releaseId, List<String> keys) implements DomainEvent {

    public RecipeFilesReleased {
        keys = List.copyOf(keys);
    }

    @Override
    public String aggregateType() {
        return "STORAGE";
    }

    @Override
    public UUID aggregateId() {
        return releaseId;
    }
}
