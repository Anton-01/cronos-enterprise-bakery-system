package com.ninsky.cronos.kitchen.section;

import java.util.List;
import java.util.UUID;

/** {@code PUT /recipe-sections/order}: listed ids take positions 0..n-1; the rest follow in their current order. */
public record RecipeSectionOrderRequest(List<UUID> ids) {

    public RecipeSectionOrderRequest {
        ids = ids == null ? List.of() : ids;
    }
}
