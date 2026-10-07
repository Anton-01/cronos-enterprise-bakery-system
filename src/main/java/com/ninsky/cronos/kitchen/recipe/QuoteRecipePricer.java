package com.ninsky.cronos.kitchen.recipe;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.infrastructure.exception.Violations;
import com.ninsky.cronos.kitchen.allergen.AllergenCatalog;
import com.ninsky.cronos.kitchen.costing.CostContext;
import com.ninsky.cronos.kitchen.recipe.file.RecipeFileCustomRepository;
import com.ninsky.cronos.kitchen.shared.AllergenRef;
import com.ninsky.cronos.kitchen.shared.KitchenMessages;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Re-prices a recipe-based quote item with the cost engine (§6.2 rule 1) and builds its snapshot
 * (rule 2): configuration, allergens, recipe version and calculation time.
 */
@Component
@RequiredArgsConstructor
public class QuoteRecipePricer {

    private static final TypeReference<List<AllergenRef>> REFS = new TypeReference<>() {
    };

    private final RecipeCustomRepository store;
    private final RecipeConfigurator configurator;
    private final RecipeFileCustomRepository files;
    private final AllergenCatalog allergens;
    private final CostContext costContext;
    private final ObjectMapper objectMapper;

    /**
     * @param marginFloor price at the recipe's target margin; below it the item gets BELOW_TARGET_MARGIN
     * @param currency    currency the cost is expressed in (tenant default)
     */
    public record Priced(UUID recipeId, BigDecimal unitCost, BigDecimal marginFloor, String currency, String configuration,
                         String allergens, long recipeVersion, Instant costCalculatedAt, String coverKey) {
    }

    /** @param index item position, for field paths ({@code items[i].…}) */
    public Priced price(UUID tenant, UUID recipeId, RecipeConfiguration configuration, int index, Instant now) {
        String path = "items[" + index + "]";
        RecipeAggregate recipe = store.findVisible(recipeId, tenant)
                .orElseThrow(() -> ApiException.invalid(path + ".recipeId", "kitchen.quote.recipeNotFound"));
        RecipeConfigurator.Configured configured = configurator.price(tenant, recipe, configuration, path + ".recipeConfiguration",
                null, null, new Violations());
        List<AllergenRef> refs = allergens.view(tenant).refs(configured.allergenIds(), KitchenMessages.language());
        String cover = files.coverKeys(List.of(recipe.id())).get(recipe.id());
        return new Priced(recipe.id(), configured.result().costPerUnit(), configured.result().suggestedUnitPrice(),
                costContext.current().currency(), configuration == null ? null : json(configuration), json(refs),
                recipe.head().version(), now, cover);
    }

    /** Snapshot allergens of a stored item; malformed JSON reads as none. */
    public List<AllergenRef> allergens(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, REFS);
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    public RecipeConfiguration configuration(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, RecipeConfiguration.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Quote snapshot not serialisable", e);
        }
    }
}
