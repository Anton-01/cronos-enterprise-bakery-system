package com.ninsky.cronos.finance.taxrate;

import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.domain.model.audit.AuditSeverity;
import com.ninsky.cronos.finance.pricing.TaxFactorType;
import com.ninsky.cronos.finance.shared.FinanceStatus;
import com.ninsky.cronos.iam.audit.AuditEvent;
import com.ninsky.cronos.iam.audit.AuditRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.time.LocalDate;
import java.util.Optional;

import static com.ninsky.cronos.finance.FinanceTestData.CLOCK;
import static com.ninsky.cronos.finance.FinanceTestData.TODAY;
import static com.ninsky.cronos.finance.FinanceTestData.taxRate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DefaultTaxRateExpiryJobTest {

    private static final LocalDate FROM = LocalDate.of(2019, 1, 1);

    @Mock
    private TaxRateRepository repository;
    @Mock
    private NamedParameterJdbcTemplate jdbc;
    @Mock
    private AuditRecorder audit;

    private DefaultTaxRateExpiryJob job;

    @BeforeEach
    void setUp() {
        job = new DefaultTaxRateExpiryJob(repository, jdbc, audit, CLOCK);
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Boolean.class))).thenReturn(false);
    }

    @Test
    void expiredDefaultStaysDefaultAndRaisesOneWarning() {
        TaxRateEntity expired = taxRate(2, "IVA_8", TaxFactorType.TASA, "8", FROM, TODAY.minusDays(1), true, FinanceStatus.ACTIVE);
        when(repository.findByIsDefaultTrue()).thenReturn(Optional.of(expired));

        job.run();

        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).recordIndependently(event.capture());
        assertThat(event.getValue().action()).isEqualTo(AuditAction.FINANCE_DEFAULT_EXPIRED);
        assertThat(event.getValue().severity()).isEqualTo(AuditSeverity.WARNING);
        assertThat(expired.isDefault()).isTrue();
    }

    @Test
    void alreadyReportedTodayIsSkipped() {
        when(repository.findByIsDefaultTrue()).thenReturn(Optional.of(
                taxRate(2, "IVA_8", TaxFactorType.TASA, "8", FROM, TODAY.minusDays(1), true, FinanceStatus.ACTIVE)));
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Boolean.class))).thenReturn(true);

        job.run();

        verify(audit, never()).recordIndependently(any());
    }

    @Test
    void stillValidDefaultIsIgnored() {
        when(repository.findByIsDefaultTrue()).thenReturn(Optional.of(
                taxRate(2, "IVA_8", TaxFactorType.TASA, "8", FROM, TODAY, true, FinanceStatus.ACTIVE)));

        job.run();

        verify(audit, never()).recordIndependently(any());
    }
}
