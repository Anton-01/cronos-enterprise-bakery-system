package com.ninsky.cronos.infrastructure.security;

import com.ninsky.cronos.eventing.application.TraceContexts;
import com.ninsky.cronos.infrastructure.config.security.DpopConfig;
import com.ninsky.cronos.infrastructure.security.dpop.DpopProofValidator;
import com.ninsky.cronos.infrastructure.security.session.SessionStateStore;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Stateless bearer authentication (performance-latency P0-4).
 * <ul>
 *   <li>No {@code Authorization} header, or a token that fails signature/expiry verification: the request continues
 *       unauthenticated and protected endpoints answer 401 without any database or Redis access.</li>
 *   <li>A verified token is enough to build the {@link CronosUserPrincipal}: user id, username, roles and permissions
 *       come from its claims. The user is never loaded per request.</li>
 *   <li>Revocation, screen lock and access changes are one Redis round trip ({@link SessionStateStore#check}). The
 *       session status is left in {@link #SESSION_STATUS_ATTRIBUTE} for the screen-lock gate.</li>
 * </ul>
 * Two {@code Authorization} schemes are accepted: {@code Bearer} and {@code DPoP} (RFC 9449, a matching {@code DPoP}
 * proof on every request). A bound token ({@code cnf} claim) presented as plain {@code Bearer} is rejected, otherwise
 * a stolen bound token could be replayed unbound. Tokens without the {@code userId} claim (issued before it existed)
 * are no longer accepted; they expired long ago.
 */
@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Request attribute holding the {@link SessionStateStore.Status} of the authenticated session. */
    public static final String SESSION_STATUS_ATTRIBUTE = JwtAuthenticationFilter.class.getName() + ".sessionStatus";

    private final JwtService jwtService;
    private final SessionStateStore sessionState;
    private final DpopProofValidator dpopProofValidator;
    private final DpopConfig dpopConfig;

    public JwtAuthenticationFilter(JwtService jwtService, SessionStateStore sessionState, DpopProofValidator dpopProofValidator,
                                   DpopConfig dpopConfig) {
        this.jwtService = jwtService;
        this.sessionState = sessionState;
        this.dpopProofValidator = dpopProofValidator;
        this.dpopConfig = dpopConfig;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            if (authHeader.startsWith("Bearer ")) {
                authenticate(authHeader.substring(7), false, request);
            } else if (authHeader.startsWith("DPoP ")) {
                authenticate(authHeader.substring(5), true, request);
            }
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(String jwt, boolean dpopScheme, HttpServletRequest request) {
        Claims claims;
        try {
            claims = jwtService.parse(jwt);
        } catch (JwtException | IllegalArgumentException invalid) {
            log.debug("Rejected access token: {}", invalid.getMessage());
            return;
        }
        UUID userId = uuid(claims.get("userId", String.class));
        if (userId == null) {
            return;
        }
        try {
            if (!isProofOfPossessionSatisfied(claims, dpopScheme, request)) {
                return;
            }
            UUID sessionId = uuid(claims.get("sessionId", String.class));
            Long accessVersion = accessVersion(claims);
            SessionStateStore.Check check = sessionState.check(sessionId, userId);
            if (check.rejects(claims.getIssuedAt(), accessVersion)) {
                return;
            }
            CronosUserPrincipal principal = CronosUserPrincipal.fromAccessToken(userId, claims.getSubject(),
                    claims.get("email", String.class), Boolean.TRUE.equals(claims.get("2faEnabled", Boolean.class)),
                    accessVersion == null ? 0 : accessVersion, strings(claims.get("roles")), strings(claims.get("permissions")));
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            request.setAttribute(SESSION_STATUS_ATTRIBUTE, check.status());
            // Events published by this request now carry the actor and session (log MDC too).
            TraceContexts.current().ifPresent(trace -> TraceContexts.bind(trace.withActor(userId, sessionId)));
        } catch (RuntimeException e) {
            log.error("Cannot set user authentication: {}", e.getMessage());
        }
    }

    private boolean isProofOfPossessionSatisfied(Claims claims, boolean dpopScheme, HttpServletRequest request) {
        Object cnf = claims.get("cnf");
        String tokenJkt = cnf instanceof Map<?, ?> map && map.get("jkt") instanceof String jkt ? jkt : null;
        if (!dpopScheme) {
            // Downgrade protection: a bound token must not be usable via plain Bearer.
            return tokenJkt == null;
        }
        if (!dpopConfig.isEnabled()) {
            return false;
        }
        String proof = request.getHeader("DPoP");
        String proofJkt = dpopProofValidator.validate(proof, request.getMethod(), request.getRequestURL().toString());
        // An unbound token presented via the DPoP scheme has no cnf to match: rejected like a mismatch.
        return Objects.equals(proofJkt, tokenJkt);
    }

    private static Long accessVersion(Claims claims) {
        Object pv = claims.get(JwtService.ACCESS_VERSION_CLAIM);
        return pv instanceof Number number ? number.longValue() : null;
    }

    private static List<String> strings(Object claim) {
        if (!(claim instanceof Collection<?> values)) {
            return List.of();
        }
        return values.stream().filter(Objects::nonNull).map(Object::toString).toList();
    }

    private static UUID uuid(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }
}
