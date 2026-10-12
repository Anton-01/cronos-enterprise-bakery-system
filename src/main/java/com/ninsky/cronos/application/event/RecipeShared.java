package com.ninsky.cronos.application.event;

import com.ninsky.cronos.eventing.domain.DomainEvent;
import com.ninsky.cronos.eventing.domain.EventType;

import java.util.UUID;

/** A recipe share link was created for an email recipient; the recipient is emailed after commit. */
@EventType("recipe.shared")
public record RecipeShared(UUID shareId, UUID recipeId, UUID sharedBy) implements DomainEvent {

    @Override
    public String aggregateType() {
        return "RECIPE_SHARE";
    }

    @Override
    public UUID aggregateId() {
        return shareId;
    }
}
