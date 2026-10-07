package com.ninsky.cronos.finance.taxrate;

import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.domain.model.audit.AuditSeverity;
import com.ninsky.cronos.finance.pricing.TaxFactorType;
import com.ninsky.cronos.finance.shared.FinanceLocks;
import com.ninsky.cronos.finance.shared.FinanceSettingsCache;
import com.ninsky.cronos.finance.shared.FinanceStatus;
import com.ninsky.cronos.finance.shared.StatusRequest;
import com.ninsky.cronos.finance.shared.VersionRequest;
import com.ninsky.cronos.iam.audit.AuditEvent;
import com.ninsky.cronos.iam.audit.AuditRecorder;
import com.ninsky.cronos.iam.shared.ActorProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.ninsky.cronos.finance.FinanceTestData.ACTOR;
import static com.ninsky.cronos.finance.FinanceTestData.CLOCK;
import static com.ninsky.cronos.finance.FinanceTestData.TODAY;
import static com.ninsky.cronos.finance.FinanceTestData.assertViolations;
import static com.ninsky.cronos.finance.FinanceTestData.taxRate;
import static com.ninsky.cronos.infrastructure.exception.ApiErrorCode.DEFAULT_LOCKED;
import static com.ninsky.cronos.infrastructure.exception.ApiErrorCode.DUPLICATE_RESOURCE;
import static com.ninsky.cronos.infrastructure.exception.ApiErrorCode.INVALID_STATE_TRANSITION;
import static com.ninsky.cronos.infrastructure.exception.ApiErrorCode.RESOURCE_IN_USE;
import static com.ninsky.cronos.infrastructure.exception.ApiErrorCode.VALIDATION_ERROR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TaxRateServiceTest {

    private static final LocalDate FROM = LocalDate.of(2010, 1, 1);

    @Mock
    private TaxRateRepository repository;
    @Mock
    private TaxRateQueryCustomRepository queries;
    @Mock
    private FinanceLocks locks;
    @Mock
    private FinanceSettingsCache cache;
    @Mock
    private AuditRecorder audit;
    @Mock
    private ActorProvider actors;

    private TaxRateService service;

    @BeforeEach
    void setUp() {
        service = new TaxRateService(repository, queries, locks, cache, audit, actors, CLOCK);
        when(actors.require()).thenReturn(ACTOR);
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(queries.find(anyLong())).thenReturn(Optional.of(mock(TaxRateResponse.class)));
    }

    private static TaxRateRequest request(String code, TaxFactorType factor, String rate, LocalDate from, LocalDate to, Long version) {
        return new TaxRateRequest(code, "Rate " + code, null, factor, rate == null ? null : new BigDecimal(rate), from, to, version);
    }

    @Test
    void createValidatesSatRulesAndDuplicatesTogether() {
        when(repository.existsByCodeAndIdNot("IVA_X", -1L)).thenReturn(true);

        assertViolations(() -> service.create(request("IVA_X", TaxFactorType.EXENTO, "0", FROM, FROM.minusDays(1), null)),
                VALIDATION_ERROR, "ratePercent", VALIDATION_ERROR, "validTo", DUPLICATE_RESOURCE, "code");
    }

    @Test
    void createSetsSatCodeServerSide() {
        when(repository.saveAndFlush(any())).thenAnswer(inv -> {
            TaxRateEntity entity = inv.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(entity, "id", 5L);
            return entity;
        });
        service.create(request("IVA_EXENTO2", TaxFactorType.EXENTO, null, FROM, null, null));

        ArgumentCaptor<TaxRateEntity> saved = ArgumentCaptor.forClass(TaxRateEntity.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getSatTaxCode()).isEqualTo("002");
        assertThat(saved.getValue().getRatePercent()).isNull();
    }

    @Test
    void appliedRateIsHistory() {
        when(repository.findById(1L)).thenReturn(Optional.of(taxRate(1, "IVA_16", TaxFactorType.TASA, "16.0000", FROM, null, false,
                FinanceStatus.ACTIVE)));
        when(queries.inUse(1L)).thenReturn(true);

        assertViolations(() -> service.update(1, request("IVA_16", TaxFactorType.TASA, "15", FROM.plusDays(1), null, 2L)),
                RESOURCE_IN_USE, "ratePercent", RESOURCE_IN_USE, "validFrom");
    }

    @Test
    void appliedRateMayBeClosedWithValidTo() {
        TaxRateEntity rate = taxRate(1, "IVA_16", TaxFactorType.TASA, "16.0000", FROM, null, false, FinanceStatus.ACTIVE);
        when(repository.findById(1L)).thenReturn(Optional.of(rate));
        when(queries.inUse(1L)).thenReturn(true);

        service.update(1, request("IVA_16", TaxFactorType.TASA, "16", FROM, TODAY, 2L));

        assertThat(rate.getValidTo()).isEqualTo(TODAY);
        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).record(event.capture());
        assertThat(event.getValue().changes()).containsOnlyKeys("validTo");
    }

    @Test
    void defaultIsLocked() {
        when(repository.findById(1L)).thenReturn(Optional.of(taxRate(1, "IVA_16", TaxFactorType.TASA, "16", FROM, null, true,
                FinanceStatus.ACTIVE)));

        assertViolations(() -> service.changeStatus(1, new StatusRequest(FinanceStatus.INACTIVE, 2L)), DEFAULT_LOCKED, "status");
        assertViolations(() -> service.delete(1), DEFAULT_LOCKED, null);
    }

    @Test
    void expiredOrScheduledRateCannotBecomeDefault() {
        when(repository.findById(2L)).thenReturn(Optional.of(taxRate(2, "IVA_8", TaxFactorType.TASA, "8", FROM, TODAY.minusDays(1), false,
                FinanceStatus.ACTIVE)));
        when(repository.findById(3L)).thenReturn(Optional.of(taxRate(3, "IVA_NEXT", TaxFactorType.TASA, "17", TODAY.plusDays(1), null, false,
                FinanceStatus.ACTIVE)));

        assertViolations(() -> service.makeDefault(2, new VersionRequest(2L)), INVALID_STATE_TRANSITION, null);
        assertViolations(() -> service.makeDefault(3, new VersionRequest(2L)), INVALID_STATE_TRANSITION, null);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void validRateBecomesDefaultWithWarning() {
        TaxRateEntity current = taxRate(1, "IVA_16", TaxFactorType.TASA, "16", FROM, null, true, FinanceStatus.ACTIVE);
        TaxRateEntity border = taxRate(2, "IVA_8", TaxFactorType.TASA, "8", FROM, TODAY, false, FinanceStatus.ACTIVE);
        when(repository.findById(2L)).thenReturn(Optional.of(border));
        when(repository.findByIsDefaultTrue()).thenReturn(Optional.of(current));

        service.makeDefault(2, new VersionRequest(2L));

        verify(locks).lockDefaults();
        assertThat(current.isDefault()).isFalse();
        assertThat(border.isDefault()).isTrue();
        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).record(event.capture());
        assertThat(event.getValue().action()).isEqualTo(AuditAction.FINANCE_DEFAULT_CHANGED);
        assertThat(event.getValue().severity()).isEqualTo(AuditSeverity.WARNING);
        verify(cache).evict();
    }

    @Test
    void catalogUsesTheTenantDate() {
        when(repository.findSelectable(FinanceStatus.ACTIVE, TODAY)).thenReturn(List.of(
                taxRate(1, "IVA_16", TaxFactorType.TASA, "16", FROM, null, true, FinanceStatus.ACTIVE)));

        assertThat(service.catalog()).extracting(TaxRateOption::code).containsExactly("IVA_16");
    }
}
