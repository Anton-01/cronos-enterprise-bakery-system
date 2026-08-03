package com.ninsky.cronos.infrastructure.persistence.recipe;

import com.ninsky.cronos.domain.port.core.RecipeRecalculationPort;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class RecipeRecalculationAdapter implements RecipeRecalculationPort {

    private final RecipeRepository recipeRepository;

    public RecipeRecalculationAdapter(RecipeRepository recipeRepository) {
        this.recipeRepository = recipeRepository;
    }

    @Override
    public void markRecipesAsNeedingRecalculation(UUID rawMaterialId) {
        recipeRepository.markRecipesAsNeedingRecalculationByRawMaterialId(rawMaterialId);
    }
}
