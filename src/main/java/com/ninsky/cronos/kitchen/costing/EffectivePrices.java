package com.ninsky.cronos.kitchen.costing;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Batched effective prices (§2), one query per call. Not cached: prices change by tenant action
 * and must be exact (K1).
 */
@Component
@RequiredArgsConstructor
public class EffectivePrices {

    private final IngredientPriceCustomRepository repository;

    /** Every requested id is present; unpriced ones map to {@link EffectivePrice#none}. */
    public Map<UUID, EffectivePrice> find(UUID tenantId, Collection<UUID> ingredientIds) {
        if (ingredientIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, EffectivePrice> found = repository.effective(tenantId, ingredientIds).stream()
                .collect(Collectors.toMap(EffectivePrice::ingredientId, Function.identity()));
        return ingredientIds.stream().distinct()
                .collect(Collectors.toMap(Function.identity(), id -> found.getOrDefault(id, EffectivePrice.none(id))));
    }

    /** Engine view of ingredients (dimension, density, effective cost). */
    public Map<UUID, CostIngredient> costIngredients(UUID tenantId, Collection<UUID> ingredientIds) {
        if (ingredientIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, EffectivePrice> prices = find(tenantId, ingredientIds);
        return repository.costHeads(ingredientIds).stream()
                .collect(Collectors.toMap(IngredientPriceCustomRepository.CostHead::id, h -> {
                    EffectivePrice price = prices.get(h.id());
                    return new CostIngredient(h.id(), h.dimension(), h.density(), price.costPerBaseUnit(), price.source());
                }));
    }
}
