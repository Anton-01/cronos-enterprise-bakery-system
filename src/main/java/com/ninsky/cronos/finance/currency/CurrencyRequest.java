package com.ninsky.cronos.finance.currency;

import com.ninsky.cronos.finance.shared.DomainRules;
import com.ninsky.cronos.finance.shared.FieldIssue;
import com.ninsky.cronos.finance.shared.SelfValidating;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Body of {@code POST} and {@code PUT /finance/currencies} ({@code version} is required on PUT). */
@DomainRules
public record CurrencyRequest(
        @NotBlank(message = "{api.validation.required}")
        @Pattern(regexp = "^[A-Z]{3}$", message = "{finance.currency.code.pattern}")
        String code,

        @NotBlank(message = "{api.validation.required}")
        @Pattern(regexp = "^\\d{3}$", message = "{finance.currency.numericCode.pattern}")
        String numericCode,

        @NotBlank(message = "{api.validation.required}")
        @Size(max = 60, message = "{finance.validation.name.length}")
        String name,

        @NotBlank(message = "{api.validation.required}")
        @Size(max = 5, message = "{finance.currency.symbol.length}")
        String symbol,

        @NotNull(message = "{api.validation.required}")
        @Min(value = 0, message = "{finance.currency.decimalPlaces.range}")
        @Max(value = 4, message = "{finance.currency.decimalPlaces.range}")
        Integer decimalPlaces,

        @NotNull(message = "{api.validation.required}")
        SymbolPosition symbolPosition,

        Long version
) implements SelfValidating {

    @Override
    public List<FieldIssue> ruleIssues() {
        return CurrencyRules.check(code, numericCode);
    }
}
