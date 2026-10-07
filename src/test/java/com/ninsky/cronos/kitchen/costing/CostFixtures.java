package com.ninsky.cronos.kitchen.costing;

import com.ninsky.cronos.domain.entity.enums.UnitDimension;
import com.ninsky.cronos.kitchen.shared.Dimension;
import com.ninsky.cronos.kitchen.unit.UnitInfo;

import java.math.BigDecimal;
import java.util.UUID;

/** Shared units and ingredients for the costing tests. */
final class CostFixtures {

    static final UnitInfo G = unit(1, "g", UnitDimension.MASS, "1");
    static final UnitInfo KG = unit(2, "kg", UnitDimension.MASS, "1000");
    static final UnitInfo ML = unit(3, "ml", UnitDimension.VOLUME, "1");
    static final UnitInfo L = unit(4, "l", UnitDimension.VOLUME, "1000");
    static final UnitInfo CUP = unit(5, "taza", UnitDimension.VOLUME, "240");
    static final UnitInfo PZ = unit(6, "pz", UnitDimension.COUNT, "1");

    /** 25 MXN/kg flour, 3.50 per egg, 30 MXN/l milk (1.03 g/ml), 180 MXN/kg butter (0.911 g/ml). */
    static final CostIngredient FLOUR = ingredient(Dimension.MASS, null, "0.025");
    static final CostIngredient EGG = ingredient(Dimension.COUNT, null, "3.5");
    static final CostIngredient MILK = ingredient(Dimension.VOLUME, "1.03", "0.03");
    static final CostIngredient BUTTER = ingredient(Dimension.MASS, "0.911", "0.18");
    static final CostIngredient UNPRICED = new CostIngredient(UUID.randomUUID(), Dimension.MASS, null, null, PriceSource.NONE);

    private CostFixtures() {
    }

    static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    static UnitInfo unit(long id, String code, UnitDimension dimension, String multiplier) {
        return new UnitInfo(id, code, code, dimension, d(multiplier), true);
    }

    static CostIngredient ingredient(Dimension base, String density, String cost) {
        return new CostIngredient(UUID.randomUUID(), base, density == null ? null : d(density), d(cost), PriceSource.OWN);
    }
}
