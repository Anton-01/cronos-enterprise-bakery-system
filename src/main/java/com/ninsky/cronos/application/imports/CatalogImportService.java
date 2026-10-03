package com.ninsky.cronos.application.imports;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ninsky.cronos.application.event.UnitCatalogChangedEvent;
import com.ninsky.cronos.application.imports.spreadsheet.RawSheet;
import com.ninsky.cronos.application.imports.spreadsheet.SpreadsheetReader;
import com.ninsky.cronos.application.imports.spreadsheet.SpreadsheetRejectedException;
import com.ninsky.cronos.application.service.audit.CatalogAuditTrail;
import com.ninsky.cronos.domain.model.audit.Actor;
import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.domain.model.imports.ImportBatch;
import com.ninsky.cronos.domain.model.imports.ImportResource;
import com.ninsky.cronos.domain.model.imports.ImportStatus;
import com.ninsky.cronos.domain.port.core.UnitCatalogLockPort;
import com.ninsky.cronos.domain.port.imports.ImportBatchRepositoryPort;
import com.ninsky.cronos.infrastructure.web.TraceIdFilter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Generic, resource-agnostic .xlsx import pipeline:
 * <ol>
 *   <li>file guards (extension, size, container signature) → SHA-256 fingerprint;</li>
 *   <li>worksheet + header validation, then row parsing — every problem collected, never fail-fast;</li>
 *   <li>dry run: plan in a read-only transaction → VALIDATED / REJECTED;</li>
 *   <li>commit: catalog lock → re-plan against the locked catalog → all-or-nothing apply →
 *       COMMITTED (ledger row + audit in the same transaction) or REJECTED (rolled back);</li>
 *   <li>every outcome lands in the append-only {@code data_import_batches} ledger.</li>
 * </ol>
 * Recommended client flow: upload with {@code dryRun=true}, show the report, then upload the same
 * file with {@code dryRun=false}.
 */
@Slf4j
@Service
public class CatalogImportService {

    private static final Set<String> ACCEPTED_CONTENT_TYPES = Set.of(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/octet-stream",
            // Windows hosts with legacy Office registrations label .xlsx uploads like this.
            "application/vnd.ms-excel");
    private static final byte[] ZIP_SIGNATURE = {0x50, 0x4B, 0x03, 0x04};
    private static final byte[] OLE2_SIGNATURE = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0};

    private final Map<ImportResource, CatalogImportHandler<?>> handlers;
    private final SpreadsheetReader spreadsheetReader;
    private final ImportBatchRepositoryPort batchRepository;
    private final ImportBatchRecorder batchRecorder;
    private final UnitCatalogLockPort catalogLock;
    private final CatalogAuditTrail auditTrail;
    private final ApplicationEventPublisher eventPublisher;
    private final MessageSource messageSource;
    private final ObjectMapper objectMapper;
    private final ImportProperties properties;
    private final TransactionTemplate readOnlyTx;
    private final TransactionTemplate writeTx;
    private final Clock clock;

    public CatalogImportService(List<CatalogImportHandler<?>> handlers, SpreadsheetReader spreadsheetReader,
                                ImportBatchRepositoryPort batchRepository, ImportBatchRecorder batchRecorder,
                                UnitCatalogLockPort catalogLock, CatalogAuditTrail auditTrail,
                                ApplicationEventPublisher eventPublisher, MessageSource messageSource,
                                ObjectMapper objectMapper, ImportProperties properties,
                                PlatformTransactionManager transactionManager, Clock clock) {
        this.handlers = handlers.stream().collect(Collectors.toMap(CatalogImportHandler::resource, Function.identity(),
                (a, b) -> {
                    throw new IllegalStateException("Two import handlers for " + a.resource());
                }, () -> new EnumMap<>(ImportResource.class)));
        this.spreadsheetReader = spreadsheetReader;
        this.batchRepository = batchRepository;
        this.batchRecorder = batchRecorder;
        this.catalogLock = catalogLock;
        this.auditTrail = auditTrail;
        this.eventPublisher = eventPublisher;
        this.messageSource = messageSource;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.readOnlyTx = new TransactionTemplate(transactionManager);
        this.readOnlyTx.setReadOnly(true);
        this.writeTx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public ImportReport importCatalog(ImportResource resource, ImportFile file, boolean dryRun, Actor actor, Locale locale) {
        CatalogImportHandler<?> handler = handlers.get(resource);
        if (handler == null) {
            throw new IllegalStateException("No import handler registered for " + resource);
        }
        return run(handler, new ImportRun(UUID.randomUUID(), resource, dryRun, file, sha256(file.content()), actor,
                MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY), clock.instant()), new IssueCollector(messageSource, locale));
    }

    static String auditDetails(UUID batchId, int row) {
        return "Bulk import batch " + batchId + ", row " + row;
    }

    private <T> ImportReport run(CatalogImportHandler<T> handler, ImportRun run, IssueCollector issues) {
        List<RowReader> rows = readRows(handler, run, issues);
        if (issues.hasErrors()) {
            return finishWithoutChanges(run, ImportStatus.REJECTED, List.of(), rows.size(), issues);
        }
        warnIfAlreadyCommitted(run, issues);

        if (run.dryRun()) {
            List<PlannedRow<T>> plan = readOnlyTx.execute(tx -> handler.plan(rows, issues));
            ImportStatus status = issues.hasErrors() ? ImportStatus.REJECTED : ImportStatus.VALIDATED;
            return finishWithoutChanges(run, status, results(plan), rows.size(), issues);
        }

        try {
            CommitAttempt attempt = writeTx.execute(tx -> {
                catalogLock.lockForWrite();
                List<PlannedRow<T>> plan = handler.plan(rows, issues);
                if (issues.hasErrors()) {
                    tx.setRollbackOnly();
                    return new CommitAttempt(null, results(plan));
                }
                ImportReport committed = report(run, ImportStatus.COMMITTED, handler.apply(plan, run.actor(), run.batchId()), rows.size(), issues);
                batchRepository.append(toBatch(committed, run.actor()));
                auditTrail.record(run.actor(), AuditAction.DATA_IMPORT_COMMITTED, CatalogAuditTrail.TARGET_DATA_IMPORT, run.batchId(),
                        null, summary(committed));
                eventPublisher.publishEvent(new UnitCatalogChangedEvent(AuditAction.DATA_IMPORT_COMMITTED.name()));
                return new CommitAttempt(committed, List.of());
            });
            if (attempt.committed() != null) {
                log.info("Import {} committed: {}", run.batchId(), summary(attempt.committed()));
                return attempt.committed();
            }
            ImportReport rejected = finishWithoutChanges(run, ImportStatus.REJECTED, attempt.plannedRows(), rows.size(), issues);
            auditTrail.record(run.actor(), AuditAction.DATA_IMPORT_REJECTED, CatalogAuditTrail.TARGET_DATA_IMPORT, run.batchId(), null, summary(rejected));
            return rejected;
        } catch (RuntimeException e) {
            issues.error(null, null, "import.failed", run.traceId());
            ImportReport failed = finishWithoutChanges(run, ImportStatus.FAILED, List.of(), rows.size(), issues);
            auditTrail.record(run.actor(), AuditAction.DATA_IMPORT_FAILED, CatalogAuditTrail.TARGET_DATA_IMPORT, run.batchId(), null, summary(failed));
            log.error("Import {} failed and was rolled back (traceId={})", run.batchId(), run.traceId(), e);
            throw e;
        }
    }

    /** File guards → worksheet → header layout → one reader per non-blank data row. */
    private List<RowReader> readRows(CatalogImportHandler<?> handler, ImportRun run, IssueCollector issues) {
        ImportFile file = run.file();
        if (!file.fileName().toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            issues.error(null, null, "import.file.extension", file.fileName());
        }
        if (file.contentType() != null && !file.contentType().isBlank()
                && !ACCEPTED_CONTENT_TYPES.contains(file.contentType().toLowerCase(Locale.ROOT))) {
            issues.error(null, null, "import.file.contentType", file.contentType());
        }
        if (file.size() == 0) {
            issues.error(null, null, "import.file.empty");
        } else if (file.size() > properties.maxFileSize().toBytes()) {
            issues.error(null, null, "import.file.tooLarge", properties.maxFileSize().toKilobytes());
        } else if (startsWith(file.content(), OLE2_SIGNATURE)) {
            issues.error(null, null, "import.file.legacyOrEncrypted");
        } else if (!startsWith(file.content(), ZIP_SIGNATURE)) {
            issues.error(null, null, "import.file.notXlsx");
        }
        if (issues.hasErrors()) {
            return List.of();
        }

        RawSheet sheet;
        try {
            sheet = spreadsheetReader.read(file.content(), handler.resource().sheetName(), properties.maxRows());
        } catch (SpreadsheetRejectedException e) {
            issues.error(null, null, e.messageKey(), e.args());
            return List.of();
        }
        SheetLayout layout = SheetLayout.resolve(sheet.headers(), handler.columns(), issues);
        if (issues.hasErrors()) {
            return List.of();
        }
        if (sheet.rows().isEmpty()) {
            issues.error(null, null, "import.file.noRows", handler.resource().sheetName());
            return List.of();
        }
        return sheet.rows().stream().map(row -> new RowReader(row, layout, issues)).toList();
    }

    private void warnIfAlreadyCommitted(ImportRun run, IssueCollector issues) {
        batchRepository.findLatestCommitted(run.resource(), run.sha256()).ifPresent(previous ->
                issues.warning(null, null, "import.file.alreadyImported", previous.id(), previous.finishedAt(), previous.actorUsername()));
    }

    private ImportReport finishWithoutChanges(ImportRun run, ImportStatus status, List<ImportRowResult> rows, int totalRows, IssueCollector issues) {
        ImportReport report = report(run, status, rows, totalRows, issues);
        try {
            batchRecorder.appendIsolated(toBatch(report, run.actor()));
        } catch (RuntimeException e) {
            // Nothing was written to the catalog; losing the ledger row of a no-op must not hide the report from the user.
            log.error("Could not record import batch {} ({}) in the ledger", run.batchId(), status, e);
        }
        log.info("Import {} finished without changes: {}", run.batchId(), summary(report));
        return report;
    }

    private ImportReport report(ImportRun run, ImportStatus status, List<ImportRowResult> rows, int totalRows, IssueCollector issues) {
        Instant finishedAt = clock.instant();
        Map<ImportAction, Long> byAction = rows.stream().collect(Collectors.groupingBy(ImportRowResult::action, Collectors.counting()));
        return new ImportReport(
                run.batchId(), run.resource(), status, run.dryRun(),
                run.file().fileName(), run.file().size(), run.sha256(),
                totalRows,
                byAction.getOrDefault(ImportAction.CREATE, 0L).intValue(),
                byAction.getOrDefault(ImportAction.UPDATE, 0L).intValue(),
                byAction.getOrDefault(ImportAction.UNCHANGED, 0L).intValue(),
                totalRows - rows.size(),
                issues.errorCount(), issues.warningCount(), issues.truncated(),
                issues.issues(), rows,
                run.actor().username(), run.traceId(),
                run.startedAt(), finishedAt, Duration.between(run.startedAt(), finishedAt).toMillis());
    }

    private ImportBatch toBatch(ImportReport report, Actor actor) {
        try {
            return new ImportBatch(report.batchId(), report.resource(), report.status(), report.dryRun(),
                    truncate(report.fileName(), 255), report.fileSizeBytes(), report.fileSha256(),
                    report.totalRows(), report.created(), report.updated(), report.unchanged(), report.rejectedRows(),
                    report.errorCount(), report.warningCount(),
                    actor.userId(), actor.username(), report.traceId(),
                    report.startedAt(), report.finishedAt(), objectMapper.writeValueAsString(report));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Import report is not serializable", e);
        }
    }

    private static List<ImportRowResult> results(List<? extends PlannedRow<?>> plan) {
        return plan == null ? List.of() : plan.stream().map(row -> row.toResult(null)).toList();
    }

    private static String summary(ImportReport report) {
        return "%s %s file=%s sha256=%s rows=%d created=%d updated=%d unchanged=%d rejectedRows=%d errors=%d warnings=%d".formatted(
                report.resource(), report.status(), report.fileName(), report.fileSha256(), report.totalRows(),
                report.created(), report.updated(), report.unchanged(), report.rejectedRows(), report.errorCount(), report.warningCount());
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is mandatory on every JVM", e);
        }
    }

    private static boolean startsWith(byte[] content, byte[] signature) {
        return content.length >= signature.length && Arrays.equals(content, 0, signature.length, signature, 0, signature.length);
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    /** Everything known about one attempt before any row is read. */
    private record ImportRun(UUID batchId, ImportResource resource, boolean dryRun, ImportFile file, String sha256,
                             Actor actor, String traceId, Instant startedAt) {
    }

    /** Result of the write transaction: either the committed report, or the rows planned before a rejection. */
    private record CommitAttempt(ImportReport committed, List<ImportRowResult> plannedRows) {
    }
}
