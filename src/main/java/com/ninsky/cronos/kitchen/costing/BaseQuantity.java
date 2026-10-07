package com.ninsky.cronos.kitchen.costing;

import com.ninsky.cronos.domain.entity.enums.UnitDimension;
import com.ninsky.cronos.kitchen.shared.Dimension;
import com.ninsky.cronos.kitchen.unit.UnitInfo;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Quantity in a unit → quantity in the ingredient's base unit (§4.4). MASS ⇄ VOLUME needs a
 * density in g/ml (canonical bases g and ml); anything else across dimensions is incompatible.
 */
public final class BaseQuantity {

    public static final int SCALE = 10;
    public static final RoundingMode MODE = RoundingMode.HALF_EVEN;

    private BaseQuantity() {
    }

    public static boolean compatible(UnitDimension unit, Dimension base, BigDecimal density) {
        UnitDimension target = base.unitDimension();
        return unit == target || (density != null && unit.isDensityBridgeableWith(target));
    }

    public static BigDecimal of(BigDecimal quantity, UnitInfo unit, Dimension base, BigDecimal density) {
        BigDecimal inUnitBase = quantity.multiply(unit.multiplierToBase(), MathContext.DECIMAL128);
        UnitDimension target = base.unitDimension();
        if (unit.dimension() == target) {
            return inUnitBase.setScale(SCALE, MODE);
        }
        if (density == null || !unit.dimension().isDensityBridgeableWith(target)) {
            throw new UnitIncompatibleException(unit.code() + " → " + base);
        }
        return target == UnitDimension.MASS
                ? inUnitBase.multiply(density).setScale(SCALE, MODE)
                : inUnitBase.divide(density, SCALE, MODE);
    }
}
