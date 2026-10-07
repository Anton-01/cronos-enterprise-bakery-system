package com.ninsky.cronos.kitchen.allergen;

import com.ninsky.cronos.kitchen.shared.KitchenStatus;
import com.ninsky.cronos.kitchen.shared.Scope;

import java.time.Instant;
import java.util.List;

/** §3.1: name, description and keywords in the caller's locale. */
public record AllergenResponse(long id, String code, String name, String description, String icon, List<String> keywords,
                               List<String> regulations, Scope scope, KitchenStatus status, long ingredientCount,
                               Instant updatedAt, long version) {

    static AllergenResponse of(AllergenCatalog.Entry entry, String language, long ingredientCount) {
        return new AllergenResponse(entry.id(), entry.code(), entry.name(language), entry.description(language), entry.icon(),
                entry.keywords(language), entry.regulations(), entry.scope(), entry.status(), ingredientCount,
                entry.updatedAt(), entry.version());
    }
}
