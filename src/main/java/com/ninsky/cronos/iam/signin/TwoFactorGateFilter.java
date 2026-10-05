package com.ninsky.cronos.iam.signin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ninsky.cronos.application.response.envelope.ApiResponseEnvelope;
import com.ninsky.cronos.iam.policy.TwoFactorRequirement;
import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.StrictContractResponder;
import com.ninsky.cronos.infrastructure.security.CronosUserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Spec §8 2FA gate: a user who must use 2FA but has not enrolled TOTP gets
 * {@code 403 TWO_FACTOR_ENROLLMENT_REQUIRED} everywhere except sign-in/out and their own account
 * (where enrolment lives). Registered in the security chain right after the JWT filter; not a bean,
 * so it never runs twice or leaks into web slices.
 */
public class TwoFactorGateFilter extends OncePerRequestFilter {

    static final List<String> ALLOWED = List.of("/auth/**", "/users/me", "/users/me/**", "/error", "/public/**");

    private final TwoFactorRequirement requirement;
    private final StrictContractResponder responder;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public TwoFactorGateFilter(TwoFactorRequirement requirement, StrictContractResponder responder, ObjectMapper objectMapper) {
        this.requirement = requirement;
        this.responder = responder;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CronosUserPrincipal principal
                && !principal.isTwoFactorEnabled() && !allowed(request) && requirement.isRequired(principal.getId())) {
            reject(request, response);
            return;
        }
        chain.doFilter(request, response);
    }

    boolean allowed(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return ALLOWED.stream().anyMatch(pattern -> matcher.match(pattern, path));
    }

    private void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ResponseEntity<ApiResponseEnvelope<Void>> body = responder.respond(ApiErrorCode.TWO_FACTOR_ENROLLMENT_REQUIRED, null,
                "security.twoFactor.enrollmentRequired", request);
        response.setStatus(body.getStatusCode().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body.getBody());
    }
}
