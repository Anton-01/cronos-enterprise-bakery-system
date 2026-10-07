package com.ninsky.cronos.kitchen.allergen;

import java.util.List;

/** {@code POST/PUT /allergens}; rules are checked together in {@link AllergenRules}. */
public record AllergenRequest(String code, String name, String description, String icon, List<String> keywords,
                              List<String> regulations, Long version) {

    public AllergenRequest {
        keywords = keywords == null ? List.of() : keywords;
        regulations = regulations == null ? List.of() : regulations;
    }
}
