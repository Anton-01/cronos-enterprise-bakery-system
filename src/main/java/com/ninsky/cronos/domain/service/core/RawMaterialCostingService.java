package com.ninsky.cronos.domain.service.core;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure domain service — no framework dependencies, no I/O. The single source of truth for the
 * "adjust for yield/waste" costing math, previously implemented twice (once in
 * {@code RawMaterialServiceImplementation}, once inline in {@code RecipeDetailService}) with a
 * real risk of the two drifting apart.
 * <p>
 * The two methods compute genuinely different metrics, not duplicates of each other:
 * {@link #calculateBaseUnitCost} is cost per unit of the material's <em>base measurement unit</em>
 * (e.g. cost per gram); {@link #calculatePurchaseUnitCost} is cost per unit of however it was
 * <em>purchased</em> (e.g. cost per bag), both adjusted for yield loss.
 */
public class RawMaterialCostingService {

    private static final int SCALE = 6;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    /**
     * Cost per unit of the purchase unit's base unit, after accounting for yield loss.
     * Formula: unitCost / (purchaseQuantity * multiplierToBase * (yieldPercentage / 100)).
     */
    public BigDecimal calculateBaseUnitCost(BigDecimal unitCost, BigDecimal purchaseQuantity,
                                             BigDecimal multiplierToBase, BigDecimal yieldPercentage) {
        BigDecimal quantityInBaseUnit = purchaseQuantity.multiply(multiplierToBase);
        BigDecimal yieldFactor = yieldPercentage.divide(ONE_HUNDRED, SCALE, RoundingMode.HALF_UP);
        BigDecimal usableQuantityInBaseUnit = quantityInBaseUnit.multiply(yieldFactor);
        return unitCost.divide(usableQuantityInBaseUnit, SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Cost per purchased unit (e.g. per bag/box as bought), after accounting for yield loss.
     * Formula: (unitCost / purchaseQuantity) * (100 / yieldPercentage).
     */
    public BigDecimal calculatePurchaseUnitCost(BigDecimal unitCost, BigDecimal purchaseQuantity, BigDecimal yieldPercentage) {
        BigDecimal costPerPurchasedUnit = unitCost.divide(purchaseQuantity, SCALE, RoundingMode.HALF_UP);
        BigDecimal yieldFactor = ONE_HUNDRED.divide(yieldPercentage, SCALE, RoundingMode.HALF_UP);
        return costPerPurchasedUnit.multiply(yieldFactor);
    }
}
