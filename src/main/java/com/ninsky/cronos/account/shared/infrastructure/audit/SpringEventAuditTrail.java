package com.ninsky.cronos.account.shared.infrastructure.audit;

import com.ninsky.cronos.account.shared.application.port.AuditTrail;
import com.ninsky.cronos.account.shared.domain.audit.AuditChange;
import com.ninsky.cronos.infrastructure.util.auth.RequestContextUtil;
import com.ninsky.cronos.infrastructure.web.TraceIdFilter;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** Publishes the change as an application event; {@link AuditLogWriter} persists it after commit. */
@Component
@RequiredArgsConstructor
public class SpringEventAuditTrail implements AuditTrail {

    private final ApplicationEventPublisher eventPublisher;
    private final RequestContextUtil requestContextUtil;

    @Override
    public void record(AuditChange change) {
        eventPublisher.publishEvent(new AuditRecorded(
                change,
                LocalDateTime.now(),
                requestContextUtil.getClientIp(),
                truncate(requestContextUtil.getUserAgent(), 500),
                MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY)));
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
