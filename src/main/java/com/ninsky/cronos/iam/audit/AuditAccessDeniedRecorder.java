package com.ninsky.cronos.iam.audit;

import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.domain.model.audit.AuditOutcome;
import com.ninsky.cronos.domain.model.audit.AuditSeverity;
import com.ninsky.cronos.infrastructure.exception.AccessDeniedRecorder;
import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Every 403 lands in the ledger in its own transaction; escalation attempts are CRITICAL. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditAccessDeniedRecorder implements AccessDeniedRecorder {

    private final AuditRecorder recorder;

    @Override
    public void record(HttpServletRequest request, String code) {
        boolean escalation = ApiErrorCode.PRIVILEGE_ESCALATION.name().equals(code);
        String endpoint = request.getMethod() + " " + request.getRequestURI();
        try {
            recorder.recordIndependently(AuditEvent.of(
                            escalation ? AuditAction.PRIVILEGE_ESCALATION_BLOCKED : AuditAction.ACCESS_DENIED,
                            AuditTargets.ENDPOINT, null, endpoint)
                    .outcome(AuditOutcome.DENIED)
                    .severity(escalation ? AuditSeverity.CRITICAL : AuditSeverity.WARNING)
                    .params(Map.of("endpoint", endpoint, "code", code))
                    .build());
        } catch (RuntimeException e) {
            log.error("Could not record {} for {}: {}", code, endpoint, e.getMessage());
        }
    }
}
