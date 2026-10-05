package com.ninsky.cronos.finance.taxrate;

import com.ninsky.cronos.finance.pricing.PricingRules;
import com.ninsky.cronos.finance.pricing.TaxFactorType;
import com.ninsky.cronos.finance.shared.FieldIssue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Pure SAT CFDI 4.0 rules of an IVA rate (spec §10.2). */
public final class TaxRateRules {

    /** SAT {@code c_Impuesto} for IVA; server-set, never accepted as input. */
    public static final String SAT_IVA = "002";

    private TaxRateRules() {
    }

    public static List<FieldIssue> check(TaxFactorType factorType, BigDecimal ratePercent, LocalDate validFrom, LocalDate validTo) {
        List<FieldIssue> issues = new ArrayList<>();
        if (factorType == TaxFactorType.EXENTO && ratePercent != null) {
            issues.add(new FieldIssue("ratePercent", "finance.taxRate.ratePercent.exento"));
        } else if (factorType == TaxFactorType.TASA && ratePercent == null) {
            issues.add(new FieldIssue("ratePercent", "api.validation.required"));
        } else if (ratePercent != null && !PricingRules.isValidRate(ratePercent)) {
            issues.add(new FieldIssue("ratePercent", "finance.taxRate.ratePercent.range"));
        }
        if (validFrom != null && validTo != null && validTo.isBefore(validFrom)) {
            issues.add(new FieldIssue("validTo", "finance.taxRate.validTo.beforeFrom"));
        }
        return List.copyOf(issues);
    }

    /** {@code validFrom ≤ day ≤ coalesce(validTo, ∞)}. */
    public static boolean isValidOn(LocalDate validFrom, LocalDate validTo, LocalDate day) {
        return !validFrom.isAfter(day) && (validTo == null || !validTo.isBefore(day));
    }
}
