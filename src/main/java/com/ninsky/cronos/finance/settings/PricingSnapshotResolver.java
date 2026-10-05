package com.ninsky.cronos.finance.settings;

import com.ninsky.cronos.finance.currency.CurrencyEntity;
import com.ninsky.cronos.finance.currency.CurrencyOption;
import com.ninsky.cronos.finance.currency.CurrencyRepository;
import com.ninsky.cronos.finance.pricing.PricingRules;
import com.ninsky.cronos.finance.pricing.PricingSnapshot;
import com.ninsky.cronos.finance.pricing.TaxFactorType;
import com.ninsky.cronos.finance.taxrate.TaxRateEntity;
import com.ninsky.cronos.finance.taxrate.TaxRateOption;
import com.ninsky.cronos.finance.taxrate.TaxRateRepository;
import com.ninsky.cronos.iam.shared.TenantTime;
import com.ninsky.cronos.infrastructure.exception.Violations;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Builds the pricing snapshot of a document (spec §11.4): omitted currency/tax fall back to the
 * tenant defaults on creation; on update, whatever the request does not change keeps the stored
 * snapshot, so later default changes never alter existing documents.
 */
@Component
@RequiredArgsConstructor
public class PricingSnapshotResolver {

    private final FinanceSettingsService settings;
    private final CurrencyRepository currencies;
    private final TaxRateRepository taxRates;
    private final Clock clock;

    /** Request fields: {@code currency} code, free-form {@code taxRate} percent, catalog {@code taxRateId}. */
    public record PricingInput(String currency, BigDecimal taxRate, Long taxRateId) {
    }

    @Transactional(readOnly = true)
    public PricingSnapshot forNewDocument(PricingInput input) {
        FinanceSettingsResponse current = settings.current();
        CurrencyOption defaultCurrency = Objects.requireNonNull(current.defaultCurrency(), "No default currency configured");
        TaxRateOption defaultTax = Objects.requireNonNull(current.defaultTaxRate(), "No default tax rate configured");
        PricingSnapshot defaults = new PricingSnapshot(defaultCurrency.code(), defaultCurrency.decimalPlaces(), defaultTax.id(),
                defaultTax.factorType(), defaultTax.ratePercent(), current.pricesIncludeTax(), current.roundingMode());
        boolean taxGiven = input.taxRate() != null || input.taxRateId() != null;
        return resolve(defaults, input, isBlank(input.currency()), !taxGiven);
    }

    @Transactional(readOnly = true)
    public PricingSnapshot forExistingDocument(PricingSnapshot stored, PricingInput input) {
        boolean keepCurrency = isBlank(input.currency()) || normalise(input.currency()).equals(stored.currencyCode());
        boolean keepTax = input.taxRateId() == null
                ? input.taxRate() == null || input.taxRate().compareTo(stored.effectiveRatePercent()) == 0
                : input.taxRateId().equals(stored.taxRateId());
        return resolve(stored, input, keepCurrency, keepTax);
    }

    private PricingSnapshot resolve(PricingSnapshot base, PricingInput input, boolean keepCurrency, boolean keepTax) {
        Violations violations = new Violations();
        PricingSnapshot withCurrency = keepCurrency ? base : currency(base, input.currency(), violations);
        PricingSnapshot result = keepTax ? withCurrency : tax(withCurrency, input, violations);
        violations.invalidIf(input.taxRateId() != null && input.taxRate() != null && result.taxRateId() != null
                && input.taxRate().compareTo(result.effectiveRatePercent()) != 0, "taxRate", "finance.quote.taxRate.mismatch");
        violations.throwIfAny();
        return result;
    }

    /** Only ACTIVE catalog codes are accepted for a new or changed currency. */
    private PricingSnapshot currency(PricingSnapshot base, String code, Violations violations) {
        Optional<CurrencyEntity> currency = currencies.findByCode(normalise(code)).filter(CurrencyEntity::isActive);
        violations.invalidIf(currency.isEmpty(), "currency", "finance.quote.currency.notActive", code);
        return currency.map(c -> base.withCurrency(c.getCode(), c.getDecimalPlaces())).orElse(base);
    }

    /** A catalog preset (ACTIVE and valid today) wins over a free-form TASA percent. */
    private PricingSnapshot tax(PricingSnapshot base, PricingInput input, Violations violations) {
        if (input.taxRateId() != null) {
            Optional<TaxRateEntity> preset = taxRates.findById(input.taxRateId())
                    .filter(rate -> rate.isActive() && rate.isValidOn(TenantTime.today(clock)));
            violations.invalidIf(preset.isEmpty(), "taxRateId", "finance.quote.taxRateId.notSelectable");
            return preset.map(rate -> base.withTax(rate.getId(), rate.getFactorType(), rate.getRatePercent())).orElse(base);
        }
        if (!PricingRules.isValidRate(input.taxRate())) {
            violations.invalid("taxRate", "finance.quote.taxRate.range");
            return base;
        }
        return base.withTax(null, TaxFactorType.TASA, input.taxRate());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String normalise(String code) {
        return code.strip().toUpperCase(Locale.ROOT);
    }
}
