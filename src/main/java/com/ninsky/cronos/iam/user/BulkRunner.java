package com.ninsky.cronos.iam.user;

import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.domain.model.audit.AuditOutcome;
import com.ninsky.cronos.iam.audit.AuditEvent;
import com.ninsky.cronos.iam.audit.AuditRecorder;
import com.ninsky.cronos.iam.audit.AuditTargets;
import com.ninsky.cronos.iam.shared.Actor;
import com.ninsky.cronos.iam.shared.IamRules;
import com.ninsky.cronos.iam.user.api.BulkResult;
import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.infrastructure.exception.Violations;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Runs one operation per user, each in its own transaction, and reports partial success (spec §3.9). */
@Slf4j
@Component
public class BulkRunner {

    static final int MAX_USERS = 200;

    private final AuditRecorder recorder;
    private final MessageSource messages;
    private final TransactionTemplate perUser;

    public BulkRunner(AuditRecorder recorder, MessageSource messages, PlatformTransactionManager transactions) {
        this.recorder = recorder;
        this.messages = messages;
        this.perUser = new TransactionTemplate(transactions);
        this.perUser.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** 1–200 distinct ids. */
    public List<UUID> userIds(Violations violations, List<UUID> raw) {
        List<UUID> ids = IamRules.distinctIds(violations, "userIds", raw);
        violations.invalidIf(raw == null || raw.isEmpty() || raw.size() > MAX_USERS, "userIds", "iam.bulk.size", MAX_USERS);
        return ids;
    }

    public BulkResult run(Actor actor, List<UUID> ids, String operation, Map<String, Object> params, Consumer<UUID> action) {
        List<UUID> succeeded = new ArrayList<>();
        List<BulkResult.Failure> failed = new ArrayList<>();
        for (UUID id : ids) {
            if (actor.id().equals(id)) {
                failed.add(failure(id, ApiErrorCode.SELF_MODIFICATION_FORBIDDEN, "iam.user.selfModification", List.of()));
                continue;
            }
            try {
                perUser.executeWithoutResult(status -> action.accept(id));
                succeeded.add(id);
            } catch (ApiException e) {
                ApiException.Violation first = e.violations().getFirst();
                failed.add(failure(id, first.code(), first.messageKey(), first.args()));
            } catch (RuntimeException e) {
                log.error("Bulk {} failed for user {}", operation, id, e);
                failed.add(failure(id, ApiErrorCode.INTERNAL_ERROR, ApiErrorCode.INTERNAL_ERROR.titleKey(), List.of()));
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>(params);
        summary.put("operation", operation);
        summary.put("requested", ids.size());
        summary.put("succeeded", succeeded.size());
        summary.put("failed", failed.size());
        recorder.recordIndependently(AuditEvent.of(AuditAction.USER_BULK_OPERATION, AuditTargets.USER, null, operation)
                .outcome(failed.isEmpty() ? AuditOutcome.SUCCESS : AuditOutcome.FAILURE)
                .params(summary)
                .build());
        return new BulkResult(succeeded, failed);
    }

    private BulkResult.Failure failure(UUID id, ApiErrorCode code, String key, List<Object> args) {
        return new BulkResult.Failure(id, code.name(),
                messages.getMessage(key, args.toArray(), key, LocaleContextHolder.getLocale()));
    }
}
