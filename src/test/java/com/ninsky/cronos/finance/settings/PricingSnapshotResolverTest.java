package com.ninsky.cronos.finance.settings;

import com.ninsky.cronos.finance.currency.CurrencyOption;
import com.ninsky.cronos.finance.currency.CurrencyRepository;
import com.ninsky.cronos.finance.currency.SymbolPosition;
import com.ninsky.cronos.finance.pricing.FinanceRoundingMode;
import com.ninsky.cronos.finance.pricing.PricingSnapshot;
import com.ninsky.cronos.finance.pricing.TaxFactorType;
import com.ninsky.cronos.finance.settings.PricingSnapshotResolver.PricingInput;
import com.ninsky.cronos.finance.shared.FinanceStatus;
import com.ninsky.cronos.finance.taxrate.TaxRateOption;
import com.ninsky.cronos.finance.taxrate.TaxRateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static com.ninsky.cronos.finance.FinanceTestData.CLOCK;
import static com.ninsky.cronos.finance.FinanceTestData.TODAY;
import static com.ninsky.cronos.finance.FinanceTestData.assertViolations;
import static com.ninsky.cronos.finance.FinanceTestData.currency;
import static com.ninsky.cronos.finance.FinanceTestData.taxRate;
import static com.ninsky.cronos.infrastructure.exception.ApiErrorCode.VALIDATION_ERROR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PricingSnapshotResolverTest {

    private static final LocalDate FROM = LocalDate.of(2010, 1, 1);

    @Mock
    private FinanceSettingsService settings;
    @Mock
    private CurrencyRepository currencies;
    @Mock
    private TaxRateRepository taxRates;

    private PricingSnapshotResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new PricingSnapshotResolver(settings, currencies, taxRates, CLOCK);
        defaults("MXN", 2, 1L, "16.0000", false, FinanceRoundingMode.HALF_UP);
        when(currencies.findByCode(anyString())).thenReturn(Optional.empty());
        when(currencies.findByCode("USD")).thenReturn(Optional.of(currency(2, "USD", "840", 2, false, FinanceStatus.ACTIVE)));
        when(currencies.findByCode("CLP")).thenReturn(Optional.of(currency(4, "CLP", "152", 0, false, FinanceStatus.ACTIVE)));
        when(currencies.findByCode("ARS")).thenReturn(Optional.of(currency(5, "ARS", "032", 2, false, FinanceStatus.INACTIVE)));
        when(taxRates.findById(anyLong())).thenReturn(Optional.empty());
        when(taxRates.findById(3L)).thenReturn(Optional.of(taxRate(3, "IVA_EXENTO", TaxFactorType.EXENTO, null, FROM, null, false,
                FinanceStatus.ACTIVE)));
        when(taxRates.findById(4L)).thenReturn(Optional.of(taxRate(4, "IVA_8", TaxFactorType.TASA, "8", FROM, TODAY.minusDays(1), false,
                FinanceStatus.ACTIVE)));
    }

    private void defaults(String code, int decimals, long taxId, String rate, boolean includeTax, FinanceRoundingMode mode) {
        when(settings.current()).thenReturn(new FinanceSettingsResponse(
                new CurrencyOption(1L, code, code, "$", decimals, SymbolPosition.BEFORE, true),
                new TaxRateOption(taxId, "IVA", "IVA", TaxFactorType.TASA, new BigDecimal(rate), true),
                includeTax, mode, null, null, 1L));
    }

    @Test
    void omittedCurrencyAndTaxUseTheDefaults() {
        PricingSnapshot snapshot = resolver.forNewDocument(new PricingInput(null, null, null));

        assertThat(snapshot).isEqualTo(new PricingSnapshot("MXN", 2, 1L, TaxFactorType.TASA, new BigDecimal("16.0000"), false,
                FinanceRoundingMode.HALF_UP));
    }

    @Test
    void explicitActiveCurrencyAndFreeFormRateAreSnapshotted() {
        PricingSnapshot snapshot = resolver.forNewDocument(new PricingInput(" clp ", new BigDecimal("8"), null));

        assertThat(snapshot.currencyCode()).isEqualTo("CLP");
        assertThat(snapshot.currencyDecimalPlaces()).isZero();
        assertThat(snapshot.taxRateId()).isNull();
        assertThat(snapshot.taxRatePercent()).isEqualByComparingTo("8");
    }

    @Test
    void inactiveOrUnknownCurrencyAndBadRateAreRejectedTogether() {
        assertViolations(() -> resolver.forNewDocument(new PricingInput("ARS", new BigDecimal("100.00001"), null)),
                VALIDATION_ERROR, "currency", VALIDATION_ERROR, "taxRate");
        assertViolations(() -> resolver.forNewDocument(new PricingInput("XXX", null, null)), VALIDATION_ERROR, "currency");
    }

    @Test
    void catalogPresetWinsAndMustMatchAnExplicitRate() {
        PricingSnapshot exempt = resolver.forNewDocument(new PricingInput(null, BigDecimal.ZERO, 3L));
        assertThat(exempt.taxFactorType()).isEqualTo(TaxFactorType.EXENTO);
        assertThat(exempt.taxRateId()).isEqualTo(3L);
        assertThat(exempt.effectiveRatePercent()).isZero();

        assertViolations(() -> resolver.forNewDocument(new PricingInput(null, new BigDecimal("16"), 3L)), VALIDATION_ERROR, "taxRate");
    }

    @Test
    void expiredOrUnknownPresetIsRejected() {
        assertViolations(() -> resolver.forNewDocument(new PricingInput(null, null, 4L)), VALIDATION_ERROR, "taxRateId");
        assertViolations(() -> resolver.forNewDocument(new PricingInput(null, null, 404L)), VALIDATION_ERROR, "taxRateId");
    }

    @Test
    void existingDocumentKeepsItsSnapshotAfterDefaultsChange() {
        PricingSnapshot stored = resolver.forNewDocument(new PricingInput(null, null, null));
        defaults("USD", 2, 9L, "8", true, FinanceRoundingMode.DOWN);

        assertThat(resolver.forExistingDocument(stored, new PricingInput(null, null, null))).isEqualTo(stored);
        assertThat(resolver.forExistingDocument(stored, new PricingInput("mxn", new BigDecimal("16"), null))).isEqualTo(stored);
        assertThat(resolver.forExistingDocument(stored, new PricingInput(null, null, 1L))).isEqualTo(stored);
    }

    @Test
    void existingDocumentKeepsADeactivatedCurrencyButCannotSwitchToOne() {
        PricingSnapshot stored = new PricingSnapshot("ARS", 2, null, TaxFactorType.TASA, new BigDecimal("16"), true, FinanceRoundingMode.UP);

        assertThat(resolver.forExistingDocument(stored, new PricingInput("ARS", null, null))).isEqualTo(stored);
        PricingSnapshot moved = resolver.forExistingDocument(stored, new PricingInput("USD", new BigDecimal("8"), null));
        assertThat(moved).isEqualTo(new PricingSnapshot("USD", 2, null, TaxFactorType.TASA, new BigDecimal("8"), true, FinanceRoundingMode.UP));

        PricingSnapshot mxnStored = stored.withCurrency("MXN", 2);
        assertViolations(() -> resolver.forExistingDocument(mxnStored, new PricingInput("ARS", null, null)), VALIDATION_ERROR, "currency");
    }
}
