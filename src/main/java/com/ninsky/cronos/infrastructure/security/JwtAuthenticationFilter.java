package com.ninsky.cronos.infrastructure.security;

import com.ninsky.cronos.infrastructure.config.security.DpopConfig;
import com.ninsky.cronos.infrastructure.security.blacklist.TokenBlacklistService;
import com.ninsky.cronos.infrastructure.security.dpop.DpopProofValidator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

/**
 * Accepts two {@code Authorization} schemes: {@code Bearer} (today's exact unbound-token flow) and
 * {@code DPoP} (RFC 9449 proof-of-possession — requires a matching {@code DPoP} proof header on
 * every request, not just at login). A token issued bound ({@code cnf} claim present) presented via
 * plain {@code Bearer} is rejected outright — otherwise a stolen bound token could just be replayed
 * unbound and DPoP would provide no real protection.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final TokenBlacklistService tokenBlacklistService;
    private final DpopProofValidator dpopProofValidator;
    private final DpopConfig dpopConfig;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final boolean dpopScheme;

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            jwt = authHeader.substring(7);
            dpopScheme = false;
        } else if (authHeader != null && authHeader.startsWith("DPoP ")) {
            jwt = authHeader.substring(5);
            dpopScheme = true;
        } else {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            final UUID userId = jwtService.extractUserId(jwt);
            final String username = jwtService.extractUsername(jwt);

            if ((userId != null || username != null) && SecurityContextHolder.getContext().getAuthentication() == null) {
                // Access tokens carry the immutable userId claim: resolve by it so a username change
                // (PUT /users/me) keeps existing sessions alive. Tokens without it fall back to sub.
                CronosUserPrincipal userDetails = userId != null
                        ? userDetailsService.loadUserById(userId)
                        : (CronosUserPrincipal) userDetailsService.loadUserByUsername(username);
                boolean tokenValid = userId != null
                        ? jwtService.isTokenValidForUserId(jwt, userId)
                        : jwtService.isTokenValid(jwt, userDetails.getUsername());

                if (tokenValid && isAccessVersionCurrent(jwt, userDetails) && !isRevoked(jwt)
                        && isProofOfPossessionSatisfied(jwt, dpopScheme, request)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception e) {
            log.error("Cannot set user authentication: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    /** Rejects tokens minted before the user's last access change (spec §1.4.5). */
    private boolean isAccessVersionCurrent(String jwt, CronosUserPrincipal principal) {
        Long tokenVersion = jwtService.extractAccessVersion(jwt);
        long current = principal.getAccessVersion();
        return tokenVersion == null ? current == 0 : tokenVersion >= current;
    }

    /** Redis-backed revocation check — one round-trip per authenticated request, after standard JWT validation. */
    private boolean isRevoked(String jwt) {
        UUID sessionId = jwtService.extractSessionId(jwt);
        if (tokenBlacklistService.isSessionBlacklisted(sessionId)) {
            return true;
        }
        UUID userId = jwtService.extractUserId(jwt);
        return tokenBlacklistService.isTokenRevokedForUser(userId, jwtService.extractIssuedAt(jwt));
    }

    private boolean isProofOfPossessionSatisfied(String jwt, boolean dpopScheme, HttpServletRequest request) {
        String tokenJkt = jwtService.extractDpopJkt(jwt);
        if (!dpopScheme) {
            // Downgrade protection: a bound token must not be usable via plain Bearer.
            return tokenJkt == null;
        }
        if (!dpopConfig.isEnabled()) {
            return false;
        }
        String proof = request.getHeader("DPoP");
        String proofJkt = dpopProofValidator.validate(proof, request.getMethod(), request.getRequestURL().toString());
        // tokenJkt == null here correctly rejects (Objects.equals(proofJkt, null) is false since
        // proofJkt is non-null on successful validation) — an unbound token presented via the DPoP
        // scheme has no cnf to match, so it's rejected same as a mismatch.
        return Objects.equals(proofJkt, tokenJkt);
    }
}
