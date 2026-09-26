package com.ninsky.cronos.account.fiscal.application;

import com.ninsky.cronos.account.fiscal.application.port.FiscalDataRepository;
import com.ninsky.cronos.account.fiscal.application.port.ZipCodeCatalog;
import com.ninsky.cronos.account.fiscal.application.rule.RegimeApplicabilityRule;
import com.ninsky.cronos.account.fiscal.application.rule.ZipCodeCatalogRule;
import com.ninsky.cronos.account.fiscal.domain.FiscalAddress;
import com.ninsky.cronos.account.fiscal.domain.FiscalData;
import com.ninsky.cronos.account.fiscal.domain.LegalName;
import com.ninsky.cronos.account.fiscal.domain.MexicanState;
import com.ninsky.cronos.account.fiscal.domain.MxZipCode;
import com.ninsky.cronos.account.fiscal.domain.Rfc;
import com.ninsky.cronos.account.fiscal.domain.TaxRegime;
import com.ninsky.cronos.account.fiscal.domain.UpsertFiscalDataCommand;
import com.ninsky.cronos.account.shared.application.port.AccountRateLimiter;
import com.ninsky.cronos.account.shared.application.port.AccountRateLimiter.Decision;
import com.ninsky.cronos.account.shared.application.port.AuditTrail;
import com.ninsky.cronos.account.shared.domain.AccountDomainError;
import com.ninsky.cronos.account.shared.domain.AccountDomainException;
import com.ninsky.cronos.account.shared.domain.ExpectedVersion;
import com.ninsky.cronos.account.shared.domain.PiiMasker;
import com.ninsky.cronos.account.shared.domain.audit.AuditChange;
import com.ninsky.cronos.account.shared.domain.audit.FieldDiff;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UpsertMyFiscalDataUseCaseTest {

    private static final UUID USER_ID = UUID.randomUUID();

    private final FiscalDataRepository repository = mock(FiscalDataRepository.class);
    private final ZipCodeCatalog zipCodeCatalog = mock(ZipCodeCatalog.class);
    private final AccountRateLimiter rateLimiter = mock(AccountRateLimiter.class);
    private final AuditTrail auditTrail = mock(AuditTrail.class);
    private final UpsertMyFiscalDataUseCase useCase = new UpsertMyFiscalDataUseCase(() -> USER_ID, repository,
            List.of(new RegimeApplicabilityRule(), new ZipCodeCatalogRule(zipCodeCatalog)), rateLimiter, auditTrail, new PiiMasker());

    @BeforeEach
    void setUp() {
        when(rateLimiter.tryConsume(any(), any())).thenReturn(Decision.allow());
        when(zipCodeCatalog.isKnown(any())).thenReturn(true);
        when(repository.save(any())).thenAnswer(inv -> {
            FiscalData data = inv.getArgument(0);
            return new FiscalData(data.userId(), data.legalName(), data.rfc(), data.taxRegime(), data.address(),
                    data.version() == null ? 0L : data.version() + 1, LocalDateTime.now());
        });
    }

    @Test
    void createsOnFirstCallAndAuditsMaskedRfc() {
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        FiscalData saved = useCase.execute(command("GODE561231GR8", TaxRegime.REGIMEN_SIMPLIFICADO_CONFIANZA, ExpectedVersion.ANY));

        assertThat(saved.version()).isZero();
        ArgumentCaptor<AuditChange> audit = ArgumentCaptor.forClass(AuditChange.class);
        verify(auditTrail).record(audit.capture());
        assertThat(audit.getValue()).isInstanceOf(AuditChange.FiscalDataCreated.class);
        assertThat(audit.getValue().changes()).containsEntry("taxId", new FieldDiff(null, "GOD*********8"));
    }

    @Test
    void updatesExistingAndAuditsOnlyChangedFields() {
        FiscalData existing = command("GODE561231GR8", TaxRegime.REGIMEN_SIMPLIFICADO_CONFIANZA, ExpectedVersion.ANY)
                .toFiscalData(USER_ID, 3L, LocalDateTime.now());
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.of(existing));

        FiscalData saved = useCase.execute(command("GODE561231GR8", TaxRegime.ACTIVIDADES_EMPRESARIALES_PROFESIONALES, ExpectedVersion.of(3)));

        assertThat(saved.version()).isEqualTo(4);
        ArgumentCaptor<AuditChange> audit = ArgumentCaptor.forClass(AuditChange.class);
        verify(auditTrail).record(audit.capture());
        assertThat(audit.getValue()).isInstanceOf(AuditChange.FiscalDataUpdated.class);
        assertThat(audit.getValue().changes()).containsOnlyKeys("taxRegime");
    }

    @Test
    void allRuleViolationsAreReportedTogether() {
        when(zipCodeCatalog.isKnown(any())).thenReturn(false);

        var thrown = catchThrowableOfType(AccountDomainException.class,
                () -> useCase.execute(command("GODE561231GR8", TaxRegime.GENERAL_LEY_PERSONAS_MORALES, ExpectedVersion.ANY)));

        assertThat(thrown.errors()).extracting(AccountDomainError::field).containsExactly("taxRegime", "address.zipCode");
        assertThat(thrown.errors().getFirst()).isInstanceOf(AccountDomainError.RegimeNotApplicable.class);
        verify(repository, never()).save(any());
    }

    @Test
    void ifMatchOnAMissingResourceFails() {
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        var thrown = catchThrowableOfType(AccountDomainException.class,
                () -> useCase.execute(command("GODE561231GR8", TaxRegime.REGIMEN_SIMPLIFICADO_CONFIANZA, ExpectedVersion.of(0))));

        assertThat(thrown.primary()).isInstanceOf(AccountDomainError.VersionMismatch.class);
    }

    private static UpsertFiscalDataCommand command(String rfc, TaxRegime regime, ExpectedVersion expectedVersion) {
        return new UpsertFiscalDataCommand(new LegalName("Pasteleria Cronos"), new Rfc(rfc), regime,
                new FiscalAddress("Av. Reforma", "222", null, "Juárez", "Cuauhtémoc", MexicanState.CMX, new MxZipCode("06600"), "MEX"),
                expectedVersion);
    }
}
