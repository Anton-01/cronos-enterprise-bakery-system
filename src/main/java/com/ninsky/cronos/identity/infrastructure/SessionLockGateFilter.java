package com.ninsky.cronos.identity.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ninsky.cronos.application.response.envelope.ApiResponseEnvelope;
import com.ninsky.cronos.eventing.application.TraceContext;
import com.ninsky.cronos.eventing.application.TraceContexts;
import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.StrictContractResponder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

/**
 * Screen lock (§2.7): an access token of a locked session answers {@code 423 SESSION_LOCKED} everywhere except the
 * unlock/sign-out endpoints. The session id comes from the authenticated token ({@code sid}). Registered right after the
 * JWT filter; not a bean, so web slices never pick it up.
 */
public class SessionLockGateFilter extends OncePerRequestFilter {

    private static final Set<String> ALLOWED = Set.of("/auth/unlock", "/auth/logout", "/auth/lock");

    private final SessionLockCustomRepository locks;
    private final StrictContractResponder responder;
    private final ObjectMapper objectMapper;

    public SessionLockGateFilter(SessionLockCustomRepository locks, StrictContractResponder responder, ObjectMapper objectMapper) {
        this.locks = locks;
        this.responder = responder;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        UUID sessionId = TraceContexts.current().map(TraceContext::sessionId).orElse(null);
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (sessionId != null && !ALLOWED.contains(path) && locks.isLocked(sessionId)) {
            ResponseEntity<ApiResponseEnvelope<Void>> body = responder.respond(ApiErrorCode.SESSION_LOCKED, null, "auth.session.locked", request);
            response.setStatus(body.getStatusCode().value());
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), body.getBody());
            return;
        }
        chain.doFilter(request, response);
    }
}
