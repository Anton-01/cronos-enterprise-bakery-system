package com.ninsky.cronos.application.response.quote;

import com.ninsky.cronos.kitchen.recipe.RecipeConfiguration;
import com.ninsky.cronos.kitchen.shared.AllergenRef;
import lombok.Builder;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
public record InternalQuoteItemResponse(
        UUID id,
        UUID recipeId,
        String productName,
        String productDescription,
        String productSize,
        BigDecimal quantity,
        BigDecimal unitCost,
        BigDecimal profitPercentage,
        BigDecimal unitPrice,
        BigDecimal subtotal,
        String notes,
        RecipeConfiguration recipeConfiguration,
        List<AllergenRef> allergens,
        Long recipeVersion,
        Instant costCalculatedAt,
        boolean priceReviewRequired
) {}
