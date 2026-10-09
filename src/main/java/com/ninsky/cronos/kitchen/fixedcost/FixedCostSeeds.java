package com.ninsky.cronos.kitchen.fixedcost;

import com.ninsky.cronos.kitchen.costing.FixedCostMethod;

import java.math.BigDecimal;
import java.util.List;

/**
 * Default fixed costs every user starts with (baking-studio §10.2): MXN reference amounts, editable and
 * deletable like any row. {@code code} is stored as {@code seed_code} only for idempotency (B4).
 */
public final class FixedCostSeeds {

    /** Currency the reference amounts are expressed in. */
    public static final String CURRENCY = "MXN";
    public static final String DESCRIPTION = "Monto de referencia; ajústalo a tu operación.";

    /**
     * @param percentage   PERCENTAGE rows only
     * @param monthlyAmount what {@code defaultAmount} was derived from, with {@code monthlyBasis} (both or neither)
     */
    public record Seed(String code, String name, String type, FixedCostMethod method, BigDecimal defaultAmount, BigDecimal percentage,
                       BigDecimal monthlyAmount, BigDecimal monthlyBasis, boolean appliesByDefault) {
    }

    public static final List<Seed> ALL = List.of(
            amount("LABOR_BAKER", "Mano de obra (repostero)", "LABOR", FixedCostMethod.HOURLY_RATE, "75.00", "12000", "160", true),
            amount("LABOR_ASSISTANT", "Ayudante de cocina", "LABOR", FixedCostMethod.HOURLY_RATE, "50.00", "8000", "160", false),
            amount("OVEN_GAS", "Gas del horno", "UTILITY", FixedCostMethod.HOURLY_RATE, "15.00", "900", "60", false),
            amount("ELECTRICITY", "Luz (batidora, refrigeración)", "UTILITY", FixedCostMethod.HOURLY_RATE, "6.00", "600", "100", false),
            amount("WATER_CLEANING", "Agua y limpieza", "UTILITY", FixedCostMethod.FIXED_PER_BATCH, "3.00", "600", "200", false),
            amount("RENT", "Renta prorrateada", "RENT", FixedCostMethod.FIXED_PER_BATCH, "40.00", "8000", "200", false),
            amount("EQUIPMENT", "Depreciación de equipo", "OVERHEAD", FixedCostMethod.FIXED_PER_BATCH, "10.00", "2000", "200", false),
            percentage("ADMIN", "Gastos administrativos", "OVERHEAD", "5"),
            percentage("MARKETING", "Publicidad y redes", "MARKETING", "3"),
            amount("CAKE_BOX", "Caja para pastel", "PACKAGING", FixedCostMethod.PER_UNIT, "18.00", null, null, false),
            amount("CAKE_BOARD", "Base de cartón para pastel", "PACKAGING", FixedCostMethod.PER_UNIT, "6.00", null, null, false),
            amount("DOME", "Domo / contenedor individual", "PACKAGING", FixedCostMethod.PER_UNIT, "4.50", null, null, false),
            amount("CUPCAKE_LINER", "Capacillo", "PACKAGING", FixedCostMethod.PER_UNIT, "0.40", null, null, false),
            amount("LABEL", "Etiqueta con ingredientes", "PACKAGING", FixedCostMethod.PER_UNIT, "1.50", null, null, false));

    private FixedCostSeeds() {
    }

    private static Seed amount(String code, String name, String type, FixedCostMethod method, String amount, String monthlyAmount,
                               String monthlyBasis, boolean appliesByDefault) {
        return new Seed(code, name, type, method, new BigDecimal(amount), BigDecimal.ZERO, decimal(monthlyAmount), decimal(monthlyBasis),
                appliesByDefault);
    }

    private static Seed percentage(String code, String name, String type, String percentage) {
        return new Seed(code, name, type, FixedCostMethod.PERCENTAGE, BigDecimal.ZERO, new BigDecimal(percentage), null, null, false);
    }

    private static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
