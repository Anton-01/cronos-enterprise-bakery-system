package com.ninsky.cronos.iam.user;

import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.iam.access.AccessGuards;
import com.ninsky.cronos.iam.access.SessionRevoker;
import com.ninsky.cronos.iam.audit.AuditEvent;
import com.ninsky.cronos.iam.audit.AuditRecorder;
import com.ninsky.cronos.iam.audit.AuditTargets;
import com.ninsky.cronos.iam.shared.Actor;
import com.ninsky.cronos.iam.shared.ActorProvider;
import com.ninsky.cronos.iam.shared.TenantTime;
import com.ninsky.cronos.iam.user.api.LoginAttempt;
import com.ninsky.cronos.iam.user.api.SessionsRevoked;
import com.ninsky.cronos.iam.user.api.IamUserSession;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.infrastructure.web.paging.CatalogPage;
import com.ninsky.cronos.infrastructure.web.paging.PageQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Live sessions and sign-in history of a user (spec §3.8). */
@Service
@RequiredArgsConstructor
public class UserSessionService {

    private final UserSessionCustomRepository sessions;
    private final UserReadCustomRepository users;
    private final AccessGuards guards;
    private final SessionRevoker revoker;
    private final ActorProvider actors;
    private final AuditRecorder recorder;
    private final MessageSource messages;
    private final Clock clock;

    /** Only active, unexpired sessions, newest activity first. */
    @Transactional(readOnly = true)
    public List<IamUserSession> sessions(UUID userId) {
        target(userId, false);
        return sessions.activeSessions(userId, TenantTime.nowLocal(clock));
    }

    @Transactional
    public void revoke(UUID userId, UUID sessionId) {
        UserRow row = target(userId, true);
        if (!sessions.revokeSession(userId, sessionId, TenantTime.nowLocal(clock))) {
            throw ApiException.notFound("iam.session.notFound");
        }
        revoker.blacklistSession(sessionId);
        recorder.record(AuditEvent.of(AuditAction.USER_SESSION_REVOKED, AuditTargets.USER, userId, row.displayName())
                .params(Map.of("sessionId", sessionId.toString())).build());
    }

    @Transactional
    public SessionsRevoked revokeAll(UUID userId) {
        UserRow row = target(userId, true);
        int revoked = sessions.countActive(userId, TenantTime.nowLocal(clock));
        revoker.revokeAll(userId, "ADMIN_REVOKED");
        recorder.record(AuditEvent.of(AuditAction.USER_SESSIONS_REVOKED, AuditTargets.USER, userId, row.displayName())
                .params(Map.of("revoked", revoked)).build());
        return new SessionsRevoked(revoked);
    }

    /** Newest first; the failure reason is generic and localised (never which credential was wrong). */
    @Transactional(readOnly = true)
    public CatalogPage<LoginAttempt> loginHistory(UUID userId, Integer page, Integer size, Locale locale) {
        target(userId, false);
        PageQuery query = PageQuery.of(page, size, null, UserSessionCustomRepository.HISTORY_SORTS, "occurredAt,desc");
        List<LoginAttempt> rows = sessions.logins(userId, query.size(), query.offset()).stream()
                .map(r -> new LoginAttempt(r.id(), r.loginAt(), r.outcome(),
                        "SUCCESS".equals(r.outcome()) ? null : messages.getMessage("iam.loginHistory." + r.outcome(), null, r.outcome(), locale),
                        r.ipAddress(), r.browser(), r.operatingSystem(), r.location()))
                .toList();
        return CatalogPage.of(rows, query, sessions.countLogins(userId));
    }

    /** Never the caller's own account; root protection applies to changes only. */
    private UserRow target(UUID userId, boolean modify) {
        Actor actor = actors.require();
        guards.requireNotSelf(actor, userId, null);
        UserRow row = users.find(userId).orElseThrow(() -> ApiException.notFound("iam.user.notFound"));
        if (modify) {
            guards.requireCanModify(actor, List.of(userId));
        }
        return row;
    }
}
