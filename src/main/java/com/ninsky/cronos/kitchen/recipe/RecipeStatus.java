package com.ninsky.cronos.kitchen.recipe;

import java.util.Map;
import java.util.Set;

/** Recipe lifecycle (§5.2): DRAFT→ACTIVE, ACTIVE→ARCHIVED|DRAFT, ARCHIVED→DRAFT. */
public enum RecipeStatus {
    DRAFT,
    ACTIVE,
    ARCHIVED;

    private static final Map<RecipeStatus, Set<RecipeStatus>> TRANSITIONS = Map.of(
            DRAFT, Set.of(ACTIVE),
            ACTIVE, Set.of(ARCHIVED, DRAFT),
            ARCHIVED, Set.of(DRAFT));

    public boolean canMoveTo(RecipeStatus target) {
        return TRANSITIONS.get(this).contains(target);
    }
}
