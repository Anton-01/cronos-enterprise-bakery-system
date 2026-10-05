package com.ninsky.cronos.finance.taxrate;

import com.ninsky.cronos.finance.pricing.TaxFactorType;
import com.ninsky.cronos.finance.shared.DomainRules;
import com.ninsky.cronos.finance.shared.FieldIssue;
import com.ninsky.cronos.finance.shared.SelfValidating;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Body of {@code POST} and {@code PUT /finance/tax-rates}; {@code satTaxCode} is ignored (server-set). */
@DomainRules
public record TaxRateRequest(
        @NotBlank(message = "{api.validation.required}")
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,29}$", message = "{finance.taxRate.code.pattern}")
        String code,

        @NotBlank(message = "{api.validation.required}")
        @Size(max = 60, message = "{finance.validation.name.length}")
        String name,

        @Size(max = 250, message = "{finance.taxRate.description.length}")
        String description,

        @NotNull(message = "{api.validation.required}")
        TaxFactorType factorType,

        BigDecimal ratePercent,

        @NotNull(message = "{api.validation.required}")
        LocalDate validFrom,

        LocalDate validTo,

        Long version
) implements SelfValidating {

    @Override
    public List<FieldIssue> ruleIssues() {
        return TaxRateRules.check(factorType, ratePercent, validFrom, validTo);
    }
}
