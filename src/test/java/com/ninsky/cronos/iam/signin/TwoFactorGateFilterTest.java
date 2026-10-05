package com.ninsky.cronos.iam.signin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ninsky.cronos.domain.model.auth.AuthUserProjection;
import com.ninsky.cronos.iam.policy.TwoFactorRequirement;
import com.ninsky.cronos.infrastructure.exception.StrictContractResponder;
import com.ninsky.cronos.infrastructure.security.CronosUserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TwoFactorGateFilterTest {

    private static final UUID USER = UUID.fromString("6d1f6f0e-2d55-4c1a-9a0c-3b8f4f6f2a11");

    /** Gate state straight from the "database"; the token's 2FA claim is irrelevant. */
    private record Requirement(boolean mustEnrol) implements TwoFactorRequirement {
        public boolean isRequired(UUID userId) {
            return mustEnrol;
        }

        public boolean mustEnrol(UUID userId) {
            return mustEnrol;
        }

        public List<String> requiredBy(UUID userId) {
            return List.of();
        }
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static TwoFactorGateFilter gate(boolean mustEnrol) {
        StaticMessageSource messages = new StaticMessageSource();
        messages.addMessage("security.twoFactor.enrollmentRequired", Locale.ROOT, "enrol first");
        return new TwoFactorGateFilter(new Requirement(mustEnrol), new StrictContractResponder(messages),
                new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    private static void signIn(boolean twoFactorClaim) {
        CronosUserPrincipal principal = new CronosUserPrincipal(new AuthUserProjection(USER, "baker", "b@c.mx", "x",
                true, true, true, true, twoFactorClaim, null, Set.of("USER"), Set.of()));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private static MockHttpServletResponse run(TwoFactorGateFilter gate, String method, String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/v1" + path);
        request.setContextPath("/api/v1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        gate.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void blocksUsersWhoMustEnrol() throws Exception {
        signIn(false);
        MockHttpServletResponse response = run(gate(true), "GET", "/iam/users");
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("TWO_FACTOR_ENROLLMENT_REQUIRED");
    }

    @ParameterizedTest
    @CsvSource({
            "GET, /users/me/two-factor", "POST, /users/me/two-factor/enrollment",
            "POST, /users/me/two-factor/enrollment/confirm", "POST, /auth/refresh", "POST, /auth/logout",
            "GET, /users/me", "GET, /auth/sessions", "GET, /auth/login-history",
            "GET, /finance/settings", "GET, /finance/currencies/catalog", "GET, /finance/tax-rates/catalog",
            "POST, /auth/login", "POST, /auth/activate", "GET, /error"})
    void allowlistStaysOpen(String method, String path) throws Exception {
        signIn(false);
        assertThat(run(gate(true), method, path).getStatus()).isEqualTo(200);
    }

    @ParameterizedTest
    @CsvSource({
            "POST, /users/me/two-factor/disable", "POST, /users/me/two-factor/recovery-codes",
            "PUT, /users/me", "GET, /users/me/fiscal", "PUT, /finance/settings", "GET, /finance/currencies",
            "DELETE, /auth/sessions", "POST, /auth/change-password", "GET, /users/medic", "GET, /iam/security-policy"})
    void everythingElseIsGated(String method, String path) throws Exception {
        signIn(false);
        assertThat(run(gate(true), method, path).getStatus()).isEqualTo(403);
    }

    @Test
    void allowlistMatchesTheContractTable() {
        assertThat(TwoFactorGateAllowlist.ENROLMENT).hasSize(10);
    }

    @Test
    void decidesFromTheDatabaseNotTheTokenClaim() throws Exception {
        signIn(true);
        assertThat(run(gate(true), "GET", "/iam/users").getStatus()).isEqualTo(403);
        signIn(false);
        assertThat(run(gate(false), "GET", "/iam/users").getStatus()).isEqualTo(200);
    }

    @Test
    void anonymousRequestsAreLeftToTheSecurityChain() throws Exception {
        assertThat(run(gate(true), "GET", "/iam/users").getStatus()).isEqualTo(200);
    }
}
