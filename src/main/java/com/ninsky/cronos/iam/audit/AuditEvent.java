package com.ninsky.cronos.iam.audit;

import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.domain.model.audit.AuditOutcome;
import com.ninsky.cronos.domain.model.audit.AuditSeverity;
import lombok.Builder;

import java.util.Map;

/**
 * One ledger entry to write. {@code params} feed the read-time summary; {@code changes} hold only
 * changed fields (never secrets). Actor, request data and time are resolved by the recorder.
 */
@Builder(toBuilder = true)
public record AuditEvent(
        AuditAction action,
        AuditOutcome outcome,
        AuditSeverity severity,
        String targetType,
        String targetId,
        String targetLabel,
        Map<String, Object> params,
        Map<String, Object> changes,
        String reason
) {
    public AuditEvent {
        outcome = outcome == null ? AuditOutcome.SUCCESS : outcome;
        severity = severity == null ? AuditSeverity.NOTICE : severity;
        params = params == null ? Map.of() : params;
        changes = changes == null ? Map.of() : changes;
    }

    public static AuditEventBuilder of(AuditAction action, String targetType, Object targetId, String targetLabel) {
        return builder().action(action).targetType(targetType)
                .targetId(targetId == null ? null : targetId.toString()).targetLabel(targetLabel);
    }
}
