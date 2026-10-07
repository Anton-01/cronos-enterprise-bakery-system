package com.ninsky.cronos.kitchen.costing;

import com.ninsky.cronos.finance.currency.CurrencyOption;
import com.ninsky.cronos.finance.pricing.FinanceRoundingMode;
import com.ninsky.cronos.finance.settings.FinanceSettingsResponse;
import com.ninsky.cronos.finance.settings.FinanceSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;
import java.util.Optional;

/** Currency and rounding rules for costing, from the (cached) finance settings. */
@Component
@RequiredArgsConstructor
public class CostContext {

    private static final String FALLBACK_CURRENCY = "MXN";

    private final FinanceSettingsService financeSettings;

    /** The default currency and its {@link CostEngine.Rules}. */
    public record Money(String currency, CostEngine.Rules rules) {
    }

    public Money current() {
        FinanceSettingsResponse settings = financeSettings.current();
        Optional<CurrencyOption> currency = Optional.ofNullable(settings.defaultCurrency());
        RoundingMode mode = Optional.ofNullable(settings.roundingMode()).map(FinanceRoundingMode::toJava).orElse(RoundingMode.HALF_UP);
        return new Money(currency.map(CurrencyOption::code).orElse(FALLBACK_CURRENCY),
                new CostEngine.Rules(currency.map(CurrencyOption::decimalPlaces).orElse(2), mode));
    }
}
