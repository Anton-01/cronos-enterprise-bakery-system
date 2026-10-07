package com.ninsky.cronos.iam.audit.query;

import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.iam.audit.AuditEvent;
import com.ninsky.cronos.iam.audit.AuditRecorder;
import com.ninsky.cronos.iam.audit.AuditTargets;
import com.ninsky.cronos.iam.shared.ActorProvider;
import com.ninsky.cronos.iam.shared.KeyedRateLimiter;
import com.ninsky.cronos.iam.shared.TenantTime;
import com.ninsky.cronos.iam.shared.UserDirectory;
import com.ninsky.cronos.iam.shared.UserRef;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.infrastructure.util.auth.RequestContextUtil;
import com.ninsky.cronos.infrastructure.web.csv.CsvWriter;
import com.ninsky.cronos.infrastructure.web.paging.CatalogPage;
import com.ninsky.cronos.infrastructure.web.paging.PageQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Audit log reads and the streamed CSV export (spec §7.3). */
@Service
public class AuditEventQueryService {

    public static final int MAX_EXPORT_ROWS = 100_000;
    static final List<String> CSV_HEADER = List.of("id", "occurredAt", "category", "action", "outcome", "severity", "actorId",
            "actorUsername", "actorDisplayName", "targetType", "targetId", "targetLabel", "summary", "reason", "ipAddress",
            "userAgent", "traceId");

    /** A ready-to-stream download. */
    public record Export(String fileName, StreamingResponseBody body) {
    }

    private final AuditEventCustomRepository repository;
    private final AuditSummaryRenderer renderer;
    private final UserDirectory directory;
    private final RequestContextUtil requestContext;
    private final ActorProvider actors;
    private final AuditRecorder recorder;
    private final TransactionTemplate readOnly;
    private final Clock clock;
    private final KeyedRateLimiter exportLimiter = new KeyedRateLimiter(5, Duration.ofMinutes(10));

    public AuditEventQueryService(AuditEventCustomRepository repository, AuditSummaryRenderer renderer, UserDirectory directory,
                                  RequestContextUtil requestContext, ActorProvider actors, AuditRecorder recorder,
                                  PlatformTransactionManager transactionManager, Clock clock) {
        this.repository = repository;
        this.renderer = renderer;
        this.directory = directory;
        this.requestContext = requestContext;
        this.actors = actors;
        this.recorder = recorder;
        this.clock = clock;
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    @Transactional(readOnly = true)
    public CatalogPage<AuditEventView> search(AuditEventFilter filter, Integer page, Integer size, Locale locale) {
        filter.validate();
        PageQuery query = PageQuery.of(page, size, null, AuditEventCustomRepository.SORTS, "occurredAt,desc");
        List<AuditEventView> content = repository.page(filter, query.offset(), query.size()).stream()
                .map(row -> view(row, locale)).toList();
        return CatalogPage.of(content, query, repository.count(filter));
    }

    /** Rate limited per actor, capped at {@value #MAX_EXPORT_ROWS} rows and audited before streaming. */
    public Export export(AuditEventFilter filter, Locale locale) {
        exportLimiter.acquire(actors.require().id());
        filter.validate();
        long rows = repository.count(filter);
        if (rows > MAX_EXPORT_ROWS) {
            throw ApiException.invalid("from", "security.audit.exportTooLarge", MAX_EXPORT_ROWS);
        }
        recorder.recordIndependently(AuditEvent.of(AuditAction.AUDIT_EXPORT, AuditTargets.AUDIT_LOG, null, null)
                .params(Map.of("detail", rows, "filters", filter.summary()))
                .build());
        String fileName = "bitacora-" + TenantTime.today(clock) + ".csv";
        return new Export(fileName, out -> readOnly.executeWithoutResult(status -> {
            CsvWriter csv = CsvWriter.open(out).row(CSV_HEADER);
            repository.stream(filter, MAX_EXPORT_ROWS, row -> csv.row(csvRow(view(row, locale))));
            csv.flush();
        }));
    }

    AuditEventView view(AuditRow row, Locale locale) {
        UserRef actor = row.actorId() == null ? null : new UserRef(row.actorId(), row.actorUsername(),
                row.actorLabel() != null ? row.actorLabel() : row.actorUsername(), avatarUrl(row.actorAvatarKey()));
        return new AuditEventView(String.valueOf(row.id()), TenantTime.toInstant(row.createdAt()), row.category(), row.action(),
                row.outcome(), row.severity(), actor,
                new AuditEventView.Target(row.targetType(), row.targetId(), renderer.targetLabel(row).orElse(null)),
                renderer.summary(row, locale), row.reason(), renderer.json(row.changesJson()), row.ipAddress(),
                requestContext.describe(row.userAgent()), row.traceId());
    }

    private static List<Object> csvRow(AuditEventView e) {
        UserRef actor = e.actor();
        return Arrays.asList(e.id(), e.occurredAt(), e.category(), e.action(), e.outcome(), e.severity(),
                actor == null ? null : actor.id(), actor == null ? null : actor.username(), actor == null ? null : actor.displayName(),
                e.target().type(), e.target().id(), e.target().label(), e.summary(), e.reason(), e.ipAddress(), e.userAgent(),
                e.traceId());
    }

    private String avatarUrl(String key) {
        try {
            return directory.avatarUrl(key);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
