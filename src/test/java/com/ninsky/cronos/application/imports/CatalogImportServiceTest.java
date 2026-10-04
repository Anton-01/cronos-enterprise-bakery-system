package com.ninsky.cronos.application.imports;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ninsky.cronos.application.imports.spreadsheet.CellValue;
import com.ninsky.cronos.application.imports.spreadsheet.RawSheet;
import com.ninsky.cronos.application.imports.spreadsheet.SheetRow;
import com.ninsky.cronos.application.imports.spreadsheet.SpreadsheetReader;
import com.ninsky.cronos.application.service.audit.CatalogAuditTrail;
import com.ninsky.cronos.domain.model.audit.Actor;
import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.domain.model.imports.ImportBatch;
import com.ninsky.cronos.domain.model.imports.ImportResource;
import com.ninsky.cronos.domain.model.imports.ImportStatus;
import com.ninsky.cronos.domain.port.core.UnitCatalogLockPort;
import com.ninsky.cronos.domain.port.imports.ImportBatchRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.util.unit.DataSize;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CatalogImportServiceTest {

    private static final Actor ACTOR = new Actor(UUID.randomUUID(), "admin");
    private static final byte[] XLSX_SIGNATURE = {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00};

    @SuppressWarnings("unchecked")
    private final CatalogImportHandler<String> handler = mock(CatalogImportHandler.class);
    private final SpreadsheetReader reader = mock(SpreadsheetReader.class);
    private final ImportBatchRepositoryPort batchRepository = mock(ImportBatchRepositoryPort.class);
    private final ImportBatchRecorder recorder = mock(ImportBatchRecorder.class);
    private final UnitCatalogLockPort lock = mock(UnitCatalogLockPort.class);
    private final CatalogAuditTrail auditTrail = mock(CatalogAuditTrail.class);
    private final PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
    private CatalogImportService service;

    @BeforeEach
    void setUp() {
        when(handler.resource()).thenReturn(ImportResource.UNIT_TYPE);
        when(handler.columns()).thenReturn(List.of(ColumnSpec.required("code")));
        when(txManager.getTransaction(any())).thenAnswer(inv -> new SimpleTransactionStatus());
        when(batchRepository.findLatestCommitted(any(), anyString())).thenReturn(Optional.empty());
        service = new CatalogImportService(List.<CatalogImportHandler<?>>of(handler), reader, batchRepository, recorder, lock, auditTrail,
                mock(ApplicationEventPublisher.class), new StaticMessageSource(),
                new ObjectMapper().registerModule(new JavaTimeModule()),
                new ImportProperties(DataSize.ofMegabytes(2), 100), txManager,
                Clock.fixed(Instant.parse("2026-10-03T12:00:00Z"), ZoneOffset.UTC));
    }

    private void sheetWithRows(String... codes) {
        List<SheetRow> rows = new ArrayList<>();
        for (int i = 0; i < codes.length; i++) {
            rows.add(new SheetRow(i + 2, List.of(new CellValue(CellValue.Kind.TEXT, codes[i]))));
        }
        when(reader.read(any(), eq("UnitTypes"), anyInt())).thenReturn(new RawSheet(List.of("code"), rows));
    }

    private ImportFile xlsx() {
        return new ImportFile("unit-types.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", XLSX_SIGNATURE);
    }

    @Test
    void rejectsANonXlsxFileWithoutReadingItAndRecordsTheAttempt() {
        ImportReport report = service.importCatalog(ImportResource.UNIT_TYPE,
                new ImportFile("units.csv", "text/csv", "code\nkg".getBytes()), false, ACTOR, Locale.ENGLISH);

        assertThat(report.status()).isEqualTo(ImportStatus.REJECTED);
        assertThat(report.issues()).extracting(ImportIssue::code)
                .containsExactlyInAnyOrder("import.file.extension", "import.file.contentType", "import.file.notXlsx");
        assertThat(report.fileSha256()).hasSize(64);
        verifyNoInteractions(reader, lock);
        verify(recorder).appendIsolated(any(ImportBatch.class));
    }

    @Test
    void dryRunValidatesWithoutLockingOrApplying() {
        sheetWithRows("MASS", "VOLUME");
        when(handler.plan(any(), any())).thenReturn(List.of(
                new PlannedRow<>(2, "MASS", ImportAction.CREATE, null, "MASS", null),
                new PlannedRow<>(3, "VOLUME", ImportAction.UNCHANGED, "VOLUME", "VOLUME", null)));

        ImportReport report = service.importCatalog(ImportResource.UNIT_TYPE, xlsx(), true, ACTOR, Locale.ENGLISH);

        assertThat(report.status()).isEqualTo(ImportStatus.VALIDATED);
        assertThat(report.totalRows()).isEqualTo(2);
        assertThat(report.created()).isEqualTo(1);
        assertThat(report.unchanged()).isEqualTo(1);
        verify(handler, never()).apply(any(), any(), any());
        verifyNoInteractions(lock);
        verify(recorder).appendIsolated(any(ImportBatch.class));
    }

    @Test
    void commitLocksAppliesAndRecordsTheBatchInTheSameTransaction() {
        sheetWithRows("MASS");
        List<PlannedRow<String>> plan = List.of(new PlannedRow<>(2, "MASS", ImportAction.CREATE, null, "MASS", null));
        when(handler.plan(any(), any())).thenReturn(plan);
        when(handler.apply(eq(plan), eq(ACTOR), any())).thenReturn(List.of(plan.getFirst().toResult(7L)));

        ImportReport report = service.importCatalog(ImportResource.UNIT_TYPE, xlsx(), false, ACTOR, Locale.ENGLISH);

        assertThat(report.status()).isEqualTo(ImportStatus.COMMITTED);
        assertThat(report.rows()).extracting(ImportRowResult::recordId).containsExactly(7L);
        verify(lock).lockForWrite();
        ArgumentCaptor<ImportBatch> batch = ArgumentCaptor.forClass(ImportBatch.class);
        verify(batchRepository).append(batch.capture());
        assertThat(batch.getValue().status()).isEqualTo(ImportStatus.COMMITTED);
        assertThat(batch.getValue().reportJson()).contains("\"status\":\"COMMITTED\"");
        verify(auditTrail).record(eq(ACTOR), eq(AuditAction.DATA_IMPORT_COMMITTED), anyString(), any(), any(), anyString());
        verify(recorder, never()).appendIsolated(any());
    }

    @Test
    void anyRowErrorRejectsTheWholeFileAndRollsBack() {
        sheetWithRows("MASS", "BAD");
        when(handler.plan(any(), any())).thenAnswer(inv -> {
            IssueCollector issues = inv.getArgument(1);
            issues.error(3, "code", "catalog.unitType.codeDuplicated", "BAD");
            return List.of(new PlannedRow<>(2, "MASS", ImportAction.CREATE, null, "MASS", null));
        });

        ImportReport report = service.importCatalog(ImportResource.UNIT_TYPE, xlsx(), false, ACTOR, Locale.ENGLISH);

        assertThat(report.status()).isEqualTo(ImportStatus.REJECTED);
        assertThat(report.rejectedRows()).isEqualTo(1);
        assertThat(report.totalRows()).isEqualTo(report.created() + report.updated() + report.unchanged() + report.rejectedRows());
        verify(handler, never()).apply(any(), any(), any());
        ArgumentCaptor<TransactionStatus> tx = ArgumentCaptor.forClass(TransactionStatus.class);
        verify(txManager).commit(tx.capture());
        assertThat(tx.getValue().isRollbackOnly()).as("nothing written: the write transaction is rolled back").isTrue();
        verify(recorder).appendIsolated(any(ImportBatch.class));
        verify(auditTrail).record(eq(ACTOR), eq(AuditAction.DATA_IMPORT_REJECTED), anyString(), any(), any(), anyString());
    }

    @Test
    void warnsWhenTheSameFileWasAlreadyCommitted() {
        sheetWithRows("MASS");
        when(handler.plan(any(), any())).thenReturn(List.of());
        when(batchRepository.findLatestCommitted(eq(ImportResource.UNIT_TYPE), anyString())).thenReturn(Optional.of(
                new ImportBatch(UUID.randomUUID(), ImportResource.UNIT_TYPE, ImportStatus.COMMITTED, false, "a.xlsx", 6, "x",
                        1, 1, 0, 0, 0, 0, 0, ACTOR.userId(), "admin", null, Instant.EPOCH, Instant.EPOCH, "{}")));

        ImportReport report = service.importCatalog(ImportResource.UNIT_TYPE, xlsx(), true, ACTOR, Locale.ENGLISH);

        assertThat(report.issues()).extracting(ImportIssue::code).containsExactly("import.file.alreadyImported");
        assertThat(report.warningCount()).isEqualTo(1);
    }
}
