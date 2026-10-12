package com.ninsky.cronos.application.service.user;

import com.ninsky.cronos.application.response.auth.LoginHistoryResponse;
import com.ninsky.cronos.application.response.auth.UserSessionResponse;
import com.ninsky.cronos.iam.shared.ActorProvider;
import com.ninsky.cronos.infrastructure.persistence.auth.adapter.UserSecurityCustomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Sessions and sign-in history of the authenticated user. One SQL statement per screen; the user comes from the
 * authenticated actor (no user row is loaded).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserSecurityDataService {

    static final int HISTORY_LIMIT = 50;

    private final UserSecurityCustomRepository security;
    private final ActorProvider actors;

    /** {@code username} is the authenticated name; the actor's id is what is queried. */
    public List<UserSessionResponse> getActiveSessionsByUsername(String username, String currentIp, String currentUserAgent) {
        log.debug("Fetching active sessions for user: {}", username);
        return security.activeSessions(actors.require().id(), currentIp, currentUserAgent);
    }

    public List<LoginHistoryResponse> getLoginHistoryByUsername(String username) {
        log.debug("Retrieving login history for user: {}", username);
        return security.loginHistory(actors.require().id(), HISTORY_LIMIT);
    }
}
