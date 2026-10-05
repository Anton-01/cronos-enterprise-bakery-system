package com.ninsky.cronos.iam.user;

import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.iam.audit.AuditEvent;
import com.ninsky.cronos.iam.audit.AuditRecorder;
import com.ninsky.cronos.iam.audit.AuditTargets;
import com.ninsky.cronos.iam.shared.ActorProvider;
import com.ninsky.cronos.iam.shared.CsvWriter;
import com.ninsky.cronos.iam.shared.KeyedRateLimiter;
import com.ninsky.cronos.iam.shared.RoleRef;
import com.ninsky.cronos.infrastructure.web.paging.PageQuery;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/** Streams the filtered user list as CSV, in chunks with batched role lookups (spec §3.10). */
@Service
public class UserExportService {

    static final List<String> HEADER = List.of("id", "username", "email", "firstName", "lastName", "status", "roles",
            "twoFactorEnabled", "lastLoginAt", "createdAt", "accessExpiresAt");
    private static final int CHUNK = 500;

    private final UserReadRepository users;
    private final ActorProvider actors;
    private final AuditRecorder recorder;
    private final TransactionTemplate readOnly;
    private final KeyedRateLimiter limiter = new KeyedRateLimiter(5, Duration.ofMinutes(10));

    public UserExportService(UserReadRepository users, ActorProvider actors, AuditRecorder recorder,
                             PlatformTransactionManager transactions) {
        this.users = users;
        this.actors = actors;
        this.recorder = recorder;
        this.readOnly = new TransactionTemplate(transactions);
        this.readOnly.setReadOnly(true);
    }

    /** Rate limit applies now; rows are read when the body is written, under the caller's security context. */
    public StreamingResponseBody export(UserSearch search, PageQuery order) {
        limiter.acquire(actors.require().id());
        SecurityContext context = SecurityContextHolder.getContext();
        return stream -> {
            SecurityContextHolder.setContext(context);
            try (CsvWriter csv = new CsvWriter(stream)) {
                csv.row(HEADER);
                long rows = Objects.requireNonNull(readOnly.execute(status -> write(search, order, csv)));
                recorder.recordIndependently(AuditEvent.of(AuditAction.USER_EXPORT, AuditTargets.USER, null, "CSV")
                        .params(filters(search, rows)).build());
            } finally {
                SecurityContextHolder.clearContext();
            }
        };
    }

    private long write(UserSearch search, PageQuery order, CsvWriter csv) {
        List<UserRow> chunk = new ArrayList<>(CHUNK);
        long[] count = {0};
        users.stream(search, order.orderBySql(), row -> {
            chunk.add(row);
            if (chunk.size() == CHUNK) {
                count[0] += flush(chunk, csv);
            }
        });
        count[0] += flush(chunk, csv);
        return count[0];
    }

    private int flush(List<UserRow> chunk, CsvWriter csv) {
        if (chunk.isEmpty()) {
            return 0;
        }
        Map<UUID, List<RoleRef>> roles = users.roles(chunk.stream().map(UserRow::id).toList());
        chunk.forEach(u -> csv.row(List.of(u.id(), u.username(), Objects.toString(u.email(), ""),
                Objects.toString(u.firstName(), ""), Objects.toString(u.lastName(), ""), u.status(),
                roles.getOrDefault(u.id(), List.of()).stream().map(RoleRef::code).collect(Collectors.joining("|")),
                u.twoFactorEnabled(), Objects.toString(u.lastLoginAt(), ""), Objects.toString(u.createdAt(), ""),
                Objects.toString(u.accessExpiresAt(), ""))));
        csv.flush();
        int size = chunk.size();
        chunk.clear();
        return size;
    }

    private static Map<String, Object> filters(UserSearch search, long rows) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("search", search.search());
        params.put("statuses", search.statuses().stream().map(Enum::name).toList());
        params.put("roleIds", search.roleIds());
        params.put("twoFactorEnabled", search.twoFactorEnabled());
        params.put("rows", rows);
        return params;
    }
}
