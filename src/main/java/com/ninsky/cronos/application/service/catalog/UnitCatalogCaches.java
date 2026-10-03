package com.ninsky.cronos.application.service.catalog;

/**
 * Cache names of the unit catalog. Every catalog write clears both — the unit picker embeds unit-type
 * name/status — after commit, via {@link com.ninsky.cronos.application.event.UnitCatalogChangedEvent}.
 */
public final class UnitCatalogCaches {

    public static final String UNIT_TYPES = "unitTypeCatalog";
    public static final String MEASUREMENT_UNITS = "measurementUnitCatalog";

    private UnitCatalogCaches() {
    }
}
