package com.ninsky.cronos.identity.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ninsky.cronos.application.response.envelope.ApiResponseEnvelope;
import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.StrictContractResponder;
import com.ninsky.cronos.infrastructure.security.JwtAuthenticationFilter;
import com.ninsky.cronos.infrastructure.security.session.SessionStateStore;
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

/**
 * Screen lock (§2.7): an access token of a locked session answers {@code 423 SESSION_LOCKED} everywhere except the
 * unlock/sign-out endpoints. The lock state is the session status the JWT filter read from Redis for this request
 * ({@link JwtAuthenticationFilter#SESSION_STATUS_ATTRIBUTE}), so the gate itself does no I/O. Registered right after the
 * JWT filter; not a bean, so web slices never pick it up.
 */
public class SessionLockGateFilter extends OncePerRequestFilter {

    private static final Set<String> ALLOWED = Set.of("/auth/unlock", "/auth/logout", "/auth/lock");

    private final StrictContractResponder responder;
    private final ObjectMapper objectMapper;

    public SessionLockGateFilter(StrictContractResponder responder, ObjectMapper objectMapper) {
        this.responder = responder;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        boolean locked = request.getAttribute(JwtAuthenticationFilter.SESSION_STATUS_ATTRIBUTE) == SessionStateStore.Status.LOCKED;
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (locked && !ALLOWED.contains(path)) {
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
