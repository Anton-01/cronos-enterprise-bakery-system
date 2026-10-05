package com.ninsky.cronos.finance.currency;

import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.domain.model.audit.AuditSeverity;
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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Map;
import java.util.Optional;

import static com.ninsky.cronos.finance.FinanceTestData.ACTOR;
import static com.ninsky.cronos.finance.FinanceTestData.ACTOR_ID;
import static com.ninsky.cronos.finance.FinanceTestData.CLOCK;
import static com.ninsky.cronos.finance.FinanceTestData.assertViolations;
import static com.ninsky.cronos.finance.FinanceTestData.currency;
import static com.ninsky.cronos.infrastructure.exception.ApiErrorCode.CONCURRENT_MODIFICATION;
import static com.ninsky.cronos.infrastructure.exception.ApiErrorCode.DEFAULT_LOCKED;
import static com.ninsky.cronos.infrastructure.exception.ApiErrorCode.DUPLICATE_RESOURCE;
import static com.ninsky.cronos.infrastructure.exception.ApiErrorCode.INVALID_STATE_TRANSITION;
import static com.ninsky.cronos.infrastructure.exception.ApiErrorCode.RESOURCE_IN_USE;
import static com.ninsky.cronos.infrastructure.exception.ApiErrorCode.VALIDATION_ERROR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CurrencyServiceTest {

    @Mock
    private CurrencyRepository repository;
    @Mock
    private CurrencyQueries queries;
    @Mock
    private FinanceLocks locks;
    @Mock
    private FinanceSettingsCache cache;
    @Mock
    private AuditRecorder audit;
    @Mock
    private ActorProvider actors;

    private CurrencyService service;

    @BeforeEach
    void setUp() {
        service = new CurrencyService(repository, queries, locks, cache, audit, actors, CLOCK);
        when(actors.require()).thenReturn(ACTOR);
        when(actors.current()).thenReturn(Optional.of(ACTOR));
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(queries.find(anyLong())).thenReturn(Optional.of(mock(CurrencyResponse.class)));
    }

    private static CurrencyRequest request(String code, String numeric, String name, int decimals, Long version) {
        return new CurrencyRequest(code, numeric, name, "$", decimals, SymbolPosition.BEFORE, version);
    }

    @Test
    void createRejectsEveryDuplicateAtOnce() {
        when(repository.existsByCodeAndIdNot("USD", -1L)).thenReturn(true);
        when(repository.existsByNumericCodeAndIdNot("840", -1L)).thenReturn(true);
        when(repository.existsByNameIgnoreCaseAndIdNot("Dólar", -1L)).thenReturn(true);

        assertViolations(() -> service.create(request("USD", "840", "Dólar", 2, null)),
                DUPLICATE_RESOURCE, "code", DUPLICATE_RESOURCE, "numericCode", DUPLICATE_RESOURCE, "name");
        verifyNoInteractions(audit);
    }

    @Test
    void createRejectsNonIsoCodesEvenWithoutBeanValidation() {
        assertViolations(() -> service.create(request("XYZ", "999", "Nope", 2, null)), VALIDATION_ERROR, "code");
    }

    @Test
    void createAuditsEveryFieldAsChanged() {
        when(repository.saveAndFlush(any())).thenAnswer(inv -> {
            CurrencyEntity entity = inv.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(entity, "id", 9L);
            return entity;
        });

        service.create(request("JPY", "392", " Yen ", 0, null));

        ArgumentCaptor<CurrencyEntity> saved = ArgumentCaptor.forClass(CurrencyEntity.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("Yen");
        assertThat(saved.getValue().getCreatedBy()).isEqualTo(ACTOR_ID);
        assertThat(saved.getValue().getStatus()).isEqualTo(FinanceStatus.ACTIVE);
        AuditEvent event = captureAudit();
        assertThat(event.action()).isEqualTo(AuditAction.CURRENCY_CREATED);
        assertThat(event.targetId()).isEqualTo("9");
        assertThat(event.changes()).containsKeys("code", "numericCode", "name", "symbol", "decimalPlaces", "symbolPosition");
    }

    @Test
    void updateRequiresVersionAndRejectsStaleOnes() {
        when(repository.findById(1L)).thenReturn(Optional.of(currency(1, "MXN", "484", 2, true, FinanceStatus.ACTIVE)));

        assertViolations(() -> service.update(1, request("MXN", "484", "Peso", 2, null)), VALIDATION_ERROR, "version");
        assertViolations(() -> service.update(1, request("MXN", "484", "Peso", 2, 2L)), CONCURRENT_MODIFICATION, "version");
    }

    @Test
    void inUseCurrencyFreezesCodeNumericAndDecimals() {
        when(repository.findById(1L)).thenReturn(Optional.of(currency(1, "MXN", "484", 2, false, FinanceStatus.ACTIVE)));
        when(queries.inUse(1L)).thenReturn(true);

        assertViolations(() -> service.update(1, request("USD", "840", "Currency MXN", 0, 3L)),
                RESOURCE_IN_USE, "code", RESOURCE_IN_USE, "numericCode", RESOURCE_IN_USE, "decimalPlaces");
    }

    @Test
    void inUseCurrencyMayStillBeRenamedAndOnlyChangesAreAudited() {
        CurrencyEntity mxn = currency(1, "MXN", "484", 2, true, FinanceStatus.ACTIVE);
        when(repository.findById(1L)).thenReturn(Optional.of(mxn));
        when(queries.inUse(1L)).thenReturn(true);

        service.update(1, request("MXN", "484", "Peso mexicano", 2, 3L));

        assertThat(mxn.getName()).isEqualTo("Peso mexicano");
        assertThat(mxn.getUpdatedBy()).isEqualTo(ACTOR_ID);
        AuditEvent event = captureAudit();
        assertThat(event.action()).isEqualTo(AuditAction.CURRENCY_UPDATED);
        assertThat(event.changes()).containsOnlyKeys("name");
        verify(cache).evict();
    }

    @Test
    void unchangedUpdateWritesNothing() {
        when(repository.findById(1L)).thenReturn(Optional.of(currency(1, "MXN", "484", 2, false, FinanceStatus.ACTIVE)));

        service.update(1, request("MXN", "484", "Currency MXN", 2, 3L));

        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(audit);
    }

    @Test
    void defaultCannotBeDeactivatedOrDeleted() {
        when(repository.findById(1L)).thenReturn(Optional.of(currency(1, "MXN", "484", 2, true, FinanceStatus.ACTIVE)));

        assertViolations(() -> service.changeStatus(1, new StatusRequest(FinanceStatus.INACTIVE, 3L)), DEFAULT_LOCKED, "status");
        assertViolations(() -> service.delete(1), DEFAULT_LOCKED, null);
        verify(repository, never()).delete(any());
    }

    @Test
    void inUseCurrencyCannotBeDeletedButCanBeDeactivated() {
        CurrencyEntity usd = currency(2, "USD", "840", 2, false, FinanceStatus.ACTIVE);
        when(repository.findById(2L)).thenReturn(Optional.of(usd));
        when(queries.inUse(2L)).thenReturn(true);

        assertViolations(() -> service.delete(2), RESOURCE_IN_USE, null);

        service.changeStatus(2, new StatusRequest(FinanceStatus.INACTIVE, 3L));
        assertThat(usd.getStatus()).isEqualTo(FinanceStatus.INACTIVE);
        AuditEvent event = captureAudit();
        assertThat(event.action()).isEqualTo(AuditAction.CURRENCY_STATUS_CHANGED);
        assertThat(event.params()).isEqualTo(Map.of("detail", "INACTIVE"));
    }

    @Test
    void deleteAuditsTheRemovedRow() {
        CurrencyEntity eur = currency(3, "EUR", "978", 2, false, FinanceStatus.ACTIVE);
        when(repository.findById(3L)).thenReturn(Optional.of(eur));

        service.delete(3);

        verify(repository).delete(eur);
        assertThat(captureAudit().action()).isEqualTo(AuditAction.CURRENCY_DELETED);
    }

    @Test
    void makeDefaultLocksThenSwitchesAtomicallyAndWarns() {
        CurrencyEntity mxn = currency(1, "MXN", "484", 2, true, FinanceStatus.ACTIVE);
        CurrencyEntity usd = currency(2, "USD", "840", 2, false, FinanceStatus.ACTIVE);
        when(repository.findById(2L)).thenReturn(Optional.of(usd));
        when(repository.findByIsDefaultTrue()).thenReturn(Optional.of(mxn));

        service.makeDefault(2, new VersionRequest(3L));

        assertThat(mxn.isDefault()).isFalse();
        assertThat(usd.isDefault()).isTrue();
        InOrder order = inOrder(locks, repository);
        order.verify(locks).lockDefaults();
        order.verify(repository).findById(2L);
        order.verify(repository).saveAndFlush(mxn);
        order.verify(repository).saveAndFlush(usd);
        AuditEvent event = captureAudit();
        assertThat(event.action()).isEqualTo(AuditAction.FINANCE_DEFAULT_CHANGED);
        assertThat(event.severity()).isEqualTo(AuditSeverity.WARNING);
        assertThat(event.changes()).containsOnlyKeys("defaultCurrency");
        verify(cache).evict();
    }

    @Test
    void inactiveCurrencyCannotBecomeDefault() {
        when(repository.findById(4L)).thenReturn(Optional.of(currency(4, "CLP", "152", 0, false, FinanceStatus.INACTIVE)));

        assertViolations(() -> service.makeDefault(4, new VersionRequest(3L)), INVALID_STATE_TRANSITION, null);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void makingTheCurrentDefaultDefaultIsANoOp() {
        when(repository.findById(1L)).thenReturn(Optional.of(currency(1, "MXN", "484", 2, true, FinanceStatus.ACTIVE)));

        service.makeDefault(1, new VersionRequest(3L));

        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(audit, cache);
    }

    @Test
    void unknownIdIsNotFound() {
        when(repository.findById(anyLong())).thenReturn(Optional.empty());

        assertViolations(() -> service.delete(99), com.ninsky.cronos.infrastructure.exception.ApiErrorCode.RESOURCE_NOT_FOUND, null);
    }

    @Test
    void catalogListsActiveDefaultFirst() {
        when(repository.findByStatusOrderByIsDefaultDescCodeAsc(FinanceStatus.ACTIVE))
                .thenReturn(java.util.List.of(currency(1, "MXN", "484", 2, true, FinanceStatus.ACTIVE)));

        assertThat(service.catalog()).extracting(CurrencyOption::code, CurrencyOption::isDefault)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("MXN", true));
    }

    private AuditEvent captureAudit() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).record(captor.capture());
        return captor.getValue();
    }
}
