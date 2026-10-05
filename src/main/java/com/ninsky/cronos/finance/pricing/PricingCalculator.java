package com.ninsky.cronos.finance.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * The single place that applies a tax rate to money (spec §11.2–11.3). Pure and stateless.
 * <pre>
 * rate = ratePercent / 100                     (EXENTO → 0)
 * pricesIncludeTax: gross = round(qty × unitPrice); net = round(gross / (1 + rate)); tax = gross − net
 * otherwise:        net   = round(qty × unitPrice); tax = round(net × rate);          gross = net + tax
 * subtotal = Σ net; tax = Σ tax (round per line, then sum); total = subtotal + tax + fees
 * </pre>
 * Rounding uses the currency scale and the tenant mode; the only division runs at scale 10 HALF_EVEN.
 */
public final class PricingCalculator {

    public static final int INTERMEDIATE_SCALE = 10;
    private static final RoundingMode INTERMEDIATE_MODE = RoundingMode.HALF_EVEN;

    public PricingResult calculate(PricingRules rules, List<PriceLine> lines, BigDecimal deliveryFee, BigDecimal extraFee) {
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(lines, "lines");
        List<LineAmounts> amounts = lines.stream().map(line -> line(rules, line)).toList();
        BigDecimal subtotal = sum(amounts.stream().map(LineAmounts::net).toList(), rules);
        BigDecimal tax = sum(amounts.stream().map(LineAmounts::tax).toList(), rules);
        BigDecimal delivery = fee(deliveryFee, "deliveryFee", rules);
        BigDecimal extra = fee(extraFee, "extraFee", rules);
        return new PricingResult(amounts, subtotal, tax, delivery, extra, subtotal.add(tax).add(delivery).add(extra));
    }

    public LineAmounts line(PricingRules rules, PriceLine line) {
        Objects.requireNonNull(line, "line");
        BigDecimal amount = round(line.quantity().multiply(line.unitPrice()), rules);
        BigDecimal rate = rules.rate();
        if (rules.pricesIncludeTax()) {
            BigDecimal net = round(amount.divide(BigDecimal.ONE.add(rate), INTERMEDIATE_SCALE, INTERMEDIATE_MODE), rules);
            return new LineAmounts(net, amount.subtract(net), amount);
        }
        BigDecimal tax = round(amount.multiply(rate), rules);
        return new LineAmounts(amount, tax, amount.add(tax));
    }

    /** Rounds a money amount to the rules' currency scale and mode. */
    public BigDecimal round(BigDecimal value, PricingRules rules) {
        return value.setScale(rules.decimalPlaces(), rules.roundingMode().toJava());
    }

    private BigDecimal fee(BigDecimal value, String name, PricingRules rules) {
        return value == null ? zero(rules) : round(requireNonNegative(value, name), rules);
    }

    private static BigDecimal sum(List<BigDecimal> values, PricingRules rules) {
        return values.stream().reduce(zero(rules), BigDecimal::add);
    }

    private static BigDecimal zero(PricingRules rules) {
        return BigDecimal.ZERO.setScale(rules.decimalPlaces());
    }

    static BigDecimal requireNonNegative(BigDecimal value, String name) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(name + " must be zero or positive");
        }
        return value;
    }
}
