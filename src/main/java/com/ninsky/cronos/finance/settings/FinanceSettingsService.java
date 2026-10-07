package com.ninsky.cronos.finance.settings;

import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.finance.currency.CurrencyOption;
import com.ninsky.cronos.finance.currency.CurrencyRepository;
import com.ninsky.cronos.finance.shared.Changes;
import com.ninsky.cronos.finance.shared.FinanceLocks;
import com.ninsky.cronos.finance.shared.FinanceSettingsCache;
import com.ninsky.cronos.finance.shared.UserRefCustomRepository;
import com.ninsky.cronos.finance.taxrate.TaxRateOption;
import com.ninsky.cronos.finance.taxrate.TaxRateRepository;
import com.ninsky.cronos.iam.audit.AuditEvent;
import com.ninsky.cronos.iam.audit.AuditRecorder;
import com.ninsky.cronos.iam.audit.AuditTargets;
import com.ninsky.cronos.iam.shared.ActorProvider;
import com.ninsky.cronos.iam.shared.TenantTime;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/** Tenant defaults + calculation rules (spec §11.1); cached 30 s in {@code financeSettings}. */
@Service
@RequiredArgsConstructor
public class FinanceSettingsService {

    private final FinanceSettingsRepository repository;
    private final CurrencyRepository currencies;
    private final TaxRateRepository taxRates;
    private final UserRefCustomRepository userRefs;
    private final FinanceSettingsCache cache;
    private final AuditRecorder audit;
    private final ActorProvider actors;
    private final Clock clock;

    @Transactional(readOnly = true)
    public FinanceSettingsResponse current() {
        return cache.get(FinanceSettingsResponse.class, this::load);
    }

    @Transactional
    public FinanceSettingsResponse update(FinanceSettingsRequest request) {
        FinanceSettingsEntity settings = settings();
        FinanceLocks.requireVersion(request.version(), settings.getVersion());
        Changes changes = Changes.start()
                .track("pricesIncludeTax", settings.isPricesIncludeTax(), request.pricesIncludeTax())
                .track("roundingMode", settings.getRoundingMode(), request.roundingMode());
        if (changes.isEmpty()) {
            return load();
        }
        settings.update(request.pricesIncludeTax(), request.roundingMode(), actors.require().id(), TenantTime.now(clock));
        repository.saveAndFlush(settings);
        audit.record(AuditEvent.of(AuditAction.FINANCE_SETTINGS_UPDATED, AuditTargets.FINANCE_SETTINGS, settings.getId(), "finance_settings")
                .changes(changes.build())
                .build());
        cache.evict();
        return load();
    }

    private FinanceSettingsResponse load() {
        FinanceSettingsEntity settings = settings();
        return new FinanceSettingsResponse(
                currencies.findByIsDefaultTrue().map(CurrencyOption::of).orElse(null),
                taxRates.findByIsDefaultTrue().map(TaxRateOption::of).orElse(null),
                settings.isPricesIncludeTax(), settings.getRoundingMode(), settings.getUpdatedAt(),
                userRefs.find(settings.getUpdatedBy()).orElse(null), settings.getVersion());
    }

    private FinanceSettingsEntity settings() {
        return repository.findById(FinanceSettingsEntity.SINGLETON_ID)
                .orElseThrow(() -> ApiException.notFound("finance.settings.notFound"));
    }
}
