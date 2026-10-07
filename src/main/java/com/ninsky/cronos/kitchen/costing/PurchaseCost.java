package com.ninsky.cronos.kitchen.costing;

import com.ninsky.cronos.kitchen.shared.Dimension;
import com.ninsky.cronos.kitchen.unit.UnitInfo;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** §4.4: costPerBaseUnit = price ÷ (baseQty × yield/100), scale 8 HALF_EVEN. */
public final class PurchaseCost {

    public static final int SCALE = 8;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private PurchaseCost() {
    }

    public static BigDecimal costPerBaseUnit(BigDecimal price, BigDecimal purchaseQuantity, UnitInfo unit, Dimension base,
                                             BigDecimal density, BigDecimal yieldPercent) {
        BigDecimal baseQuantity = BaseQuantity.of(purchaseQuantity, unit, base, density);
        BigDecimal usable = baseQuantity.multiply(yieldPercent).divide(HUNDRED, CostEngine.SCALE, RoundingMode.HALF_EVEN);
        return price.divide(usable, SCALE, RoundingMode.HALF_EVEN);
    }
}
