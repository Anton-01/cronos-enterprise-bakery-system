package com.ninsky.cronos.application.service.audit;

import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.domain.model.audit.AuditLogEntry;
import com.ninsky.cronos.domain.port.audit.AuditLogPort;
import com.ninsky.cronos.domain.port.auth.UserRepositoryPort;
import com.ninsky.cronos.infrastructure.util.auth.RequestContextUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogPort auditLogPort;
    private final UserRepositoryPort userRepository;
    private final RequestContextUtil requestContextUtil;

    public void record(String actorUsername, AuditAction action, String targetType, String targetId, String details) {
        AuditLogEntry entry = AuditLogEntry.builder()
                .actorUserId(userRepository.findByUsername(actorUsername).map(u -> u.getId()).orElse(null))
                .actorUsername(actorUsername)
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .details(details)
                .ipAddress(requestContextUtil.getClientIp())
                .userAgent(requestContextUtil.getUserAgent())
                .build();

        auditLogPort.save(entry);
        log.info("Audit: {} by {} on {}/{}", action, actorUsername, targetType, targetId);
    }
}
