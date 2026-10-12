package com.ninsky.cronos.infrastructure.security;

import com.ninsky.cronos.domain.model.auth.User;
import com.ninsky.cronos.infrastructure.config.security.DpopConfig;
import com.ninsky.cronos.infrastructure.config.security.JwtConfig;
import com.ninsky.cronos.infrastructure.security.dpop.DpopProofValidator;
import com.ninsky.cronos.infrastructure.security.session.SessionStateStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** performance-latency P0-4: the filter authenticates from the token alone and asks Redis once for the session state. */
class JwtAuthenticationFilterTest {

    private final JwtService jwtService = new JwtService(jwtConfig());
    private final SessionStateStore sessionState = mock(SessionStateStore.class);
    private final DpopProofValidator dpop = mock(DpopProofValidator.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, sessionState, dpop, new DpopConfig());

    private final UUID userId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();

    private static JwtConfig jwtConfig() {
        JwtConfig config = new JwtConfig();
        config.setSecret("test-secret-for-hs512-must-be-at-least-sixty-four-bytes-long-0123456789abcdef");
        config.setAccessTokenExpiration(60_000L);
        config.setIssuer("test");
        return config;
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void noTokenTouchesNeitherRedisNorTheDatabase() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/ingredients");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(sessionState, dpop);
    }

    @Test
    void aTamperedTokenIsRejectedBeforeAnyIo() throws Exception {
        MockHttpServletRequest request = bearer(token(3L) + "x");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(sessionState);
    }

    @Test
    void aValidTokenBuildsThePrincipalFromItsClaims() throws Exception {
        when(sessionState.check(sessionId, userId)).thenReturn(new SessionStateStore.Check(SessionStateStore.Status.ACTIVE, false, null, 3L));
        MockHttpServletRequest request = bearer(token(3L));

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        CronosUserPrincipal principal = (CronosUserPrincipal) authentication.getPrincipal();
        assertThat(principal.getId()).isEqualTo(userId);
        assertThat(principal.getUsername()).isEqualTo("ana");
        assertThat(principal.getAccessVersion()).isEqualTo(3L);
        assertThat(principal.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_ADMIN", "RECIPE.READ");
        assertThat(request.getAttribute(JwtAuthenticationFilter.SESSION_STATUS_ATTRIBUTE)).isEqualTo(SessionStateStore.Status.ACTIVE);
    }

    @Test
    void aLockedSessionAuthenticatesButCarriesItsStatusForTheGate() throws Exception {
        when(sessionState.check(sessionId, userId)).thenReturn(new SessionStateStore.Check(SessionStateStore.Status.LOCKED, false, null, null));
        MockHttpServletRequest request = bearer(token(3L));

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(request.getAttribute(JwtAuthenticationFilter.SESSION_STATUS_ATTRIBUTE)).isEqualTo(SessionStateStore.Status.LOCKED);
    }

    @Test
    void revokedSessionsRevokedUsersAndOutdatedAccessVersionsAreRefused() throws Exception {
        for (SessionStateStore.Check check : List.of(
                new SessionStateStore.Check(SessionStateStore.Status.REVOKED, false, null, null),
                new SessionStateStore.Check(SessionStateStore.Status.ACTIVE, true, null, null),
                new SessionStateStore.Check(SessionStateStore.Status.ACTIVE, false, System.currentTimeMillis() + 60_000, null),
                new SessionStateStore.Check(SessionStateStore.Status.ACTIVE, false, null, 4L))) {
            SecurityContextHolder.clearContext();
            when(sessionState.check(any(), any())).thenReturn(check);

            filter.doFilter(bearer(token(3L)), new MockHttpServletResponse(), new MockFilterChain());

            assertThat(SecurityContextHolder.getContext().getAuthentication()).as(check.toString()).isNull();
        }
    }

    @Test
    void aBoundTokenPresentedAsBearerIsRefused() throws Exception {
        User user = User.builder().id(userId).username("ana").email("ana@x.mx").build();
        String bound = jwtService.generateAccessToken(user, sessionId, List.of("ADMIN"), List.of(), "jkt-thumbprint", 3L);

        filter.doFilter(bearer(bound), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(sessionState);
    }

    @Test
    void theCutoffComparesIssueTimes() {
        SessionStateStore.Check check = new SessionStateStore.Check(SessionStateStore.Status.ACTIVE, false, 10_000L, null);

        assertThat(check.rejects(new Date(9_000), 1L)).isTrue();
        assertThat(check.rejects(new Date(10_000), 1L)).isFalse();
        assertThat(new SessionStateStore.Check(SessionStateStore.Status.ACTIVE, false, null, 2L).rejects(new Date(), null)).isTrue();
        assertThat(new SessionStateStore.Check(SessionStateStore.Status.ACTIVE, false, null, 0L).rejects(new Date(), null)).isFalse();
    }

    private String token(long accessVersion) {
        User user = User.builder().id(userId).username("ana").email("ana@x.mx").build();
        return jwtService.generateAccessToken(user, sessionId, List.of("ADMIN"), List.of("RECIPE.READ"), null, accessVersion);
    }

    private static MockHttpServletRequest bearer(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/ingredients");
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }
}
