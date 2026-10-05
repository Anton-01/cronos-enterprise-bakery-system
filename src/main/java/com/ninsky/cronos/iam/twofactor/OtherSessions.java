package com.ninsky.cronos.iam.twofactor;

import com.ninsky.cronos.application.service.auth.SessionManagementService;
import com.ninsky.cronos.domain.port.auth.UserSessionRepositoryPort;
import com.ninsky.cronos.iam.shared.TenantTime;
import com.ninsky.cronos.infrastructure.security.JwtService;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/** Ends every session of a user except the one making the current request. */
@Component
public class OtherSessions {

    private final UserSessionRepositoryPort sessions;
    private final SessionManagementService management;
    private final JwtService jwt;
    private final Clock clock;

    public OtherSessions(UserSessionRepositoryPort sessions, SessionManagementService management, JwtService jwt, Clock clock) {
        this.sessions = sessions;
        this.management = management;
        this.jwt = jwt;
        this.clock = clock;
    }

    /** @return how many sessions were ended */
    public int revoke(UUID userId, String reason) {
        Optional<UUID> current = currentSessionId();
        LocalDateTime now = TenantTime.nowLocal(clock);
        var others = sessions.findByUserIdAndIsActiveTrueOrderByCreatedAtAsc(userId).stream()
                .filter(session -> current.map(id -> !id.equals(session.getId())).orElse(true))
                .toList();
        others.forEach(session -> management.terminate(session, reason, now));
        return others.size();
    }

    private Optional<UUID> currentSessionId() {
        return Optional.ofNullable(RequestContextHolder.getRequestAttributes())
                .filter(ServletRequestAttributes.class::isInstance)
                .map(attributes -> ((ServletRequestAttributes) attributes).getRequest().getHeader("Authorization"))
                .filter(header -> header.startsWith("Bearer ") || header.startsWith("DPoP "))
                .map(header -> header.substring(header.indexOf(' ') + 1))
                .map(jwt::extractSessionId);
    }
}
