package com.ninsky.cronos.domain.model.core;

import java.util.Set;

/**
 * Measurement-unit codes other modules look up literally (raw-material density rules are always
 * expressed as grams per cup / tablespoon / teaspoon). Renaming, deactivating or deleting them
 * would break those flows, so the catalog refuses to.
 */
public final class ReservedUnitCodes {

    public static final String GRAM = "g";
    public static final String CUP = "cup";
    public static final String TABLESPOON = "tbsp";
    public static final String TEASPOON = "tsp";

    public static final Set<String> ALL = Set.of(GRAM, CUP, TABLESPOON, TEASPOON);

    private ReservedUnitCodes() {
    }

    public static boolean isReserved(String code) {
        return code != null && ALL.contains(code);
    }
}
