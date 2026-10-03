package com.ninsky.cronos.application.service.audit;

import com.ninsky.cronos.application.event.CatalogAuditEvent;
import com.ninsky.cronos.domain.model.audit.Actor;
import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.domain.model.audit.FieldChange;
import com.ninsky.cronos.infrastructure.util.auth.RequestContextUtil;
import com.ninsky.cronos.infrastructure.web.TraceIdFilter;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.SequencedMap;

/**
 * Records master-data changes (unit catalog, bulk imports) into the immutable {@code audit_log}
 * ledger. Only publishes: {@link com.ninsky.cronos.infrastructure.audit.CatalogAuditLogWriter}
 * persists after commit, so a rolled-back change never leaves an audit row behind.
 */
@Service
@RequiredArgsConstructor
public class CatalogAuditTrail {

    public static final String TARGET_UNIT_TYPE = "UNIT_TYPE";
    public static final String TARGET_MEASUREMENT_UNIT = "MEASUREMENT_UNIT";
    public static final String TARGET_DATA_IMPORT = "DATA_IMPORT";

    private static final int USER_AGENT_MAX_LENGTH = 500;
    private static final int IP_MAX_LENGTH = 45;

    private final ApplicationEventPublisher eventPublisher;
    private final RequestContextUtil requestContextUtil;

    public void record(Actor actor, AuditAction action, String targetType, Object targetId,
                       SequencedMap<String, FieldChange> changes, String details) {
        eventPublisher.publishEvent(new CatalogAuditEvent(
                action,
                targetType,
                targetId == null ? null : targetId.toString(),
                actor,
                changes,
                details,
                LocalDateTime.now(),
                truncate(requestContextUtil.getClientIp(), IP_MAX_LENGTH),
                truncate(requestContextUtil.getUserAgent(), USER_AGENT_MAX_LENGTH),
                MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY)));
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
