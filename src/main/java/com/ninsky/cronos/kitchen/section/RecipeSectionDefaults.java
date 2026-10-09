package com.ninsky.cronos.kitchen.section;

import java.util.List;

/** Default section labels every user starts with, in this order (baking-studio §10.1). */
public final class RecipeSectionDefaults {

    public record Seed(String code, String name) {
    }

    public static final List<Seed> ALL = List.of(
            new Seed("BASE", "Base"),
            new Seed("SPONGE", "Bizcocho"),
            new Seed("DOUGH", "Masa"),
            new Seed("FILLING", "Relleno"),
            new Seed("SOAK", "Baño / almíbar"),
            new Seed("CREAM", "Crema / betún"),
            new Seed("COVERING", "Cubierta"),
            new Seed("GLAZE", "Glaseado"),
            new Seed("DECORATION", "Decoración"),
            new Seed("ASSEMBLY", "Montaje"));

    private RecipeSectionDefaults() {
    }
}
