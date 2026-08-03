package com.ninsky.cronos.domain.port.core;

import java.util.UUID;

/**
 * Outbound port the catalog module uses to notify the recipe module that a raw material's cost
 * changed. Today this is a direct call (no queue/broker); a real domain-event-based version is
 * planned for a later phase — this port exists so {@code RawMaterialServiceImplementation} never
 * depends on the recipe module's own {@code RecipeRepository} directly (that dependency ran
 * backwards: catalog reaching into recipe).
 */
public interface RecipeRecalculationPort {

    void markRecipesAsNeedingRecalculation(UUID rawMaterialId);
}
