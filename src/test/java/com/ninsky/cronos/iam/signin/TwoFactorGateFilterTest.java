package com.ninsky.cronos.iam.signin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ninsky.cronos.domain.model.auth.AuthUserProjection;
import com.ninsky.cronos.iam.policy.TwoFactorRequirement;
import com.ninsky.cronos.infrastructure.exception.StrictContractResponder;
import com.ninsky.cronos.infrastructure.security.CronosUserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TwoFactorGateFilterTest {

    private static final UUID USER = UUID.fromString("6d1f6f0e-2d55-4c1a-9a0c-3b8f4f6f2a11");

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static TwoFactorGateFilter gate(boolean required) {
        StaticMessageSource messages = new StaticMessageSource();
        messages.addMessage("security.twoFactor.enrollmentRequired", Locale.ROOT, "enrol first");
        TwoFactorRequirement requirement = userId -> required;
        return new TwoFactorGateFilter(requirement, new StrictContractResponder(messages),
                new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    private static void signIn(boolean twoFactorEnabled) {
        CronosUserPrincipal principal = new CronosUserPrincipal(new AuthUserProjection(USER, "baker", "b@c.mx", "x",
                true, true, true, true, twoFactorEnabled, null, Set.of("USER"), Set.of()));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private static MockHttpServletResponse run(TwoFactorGateFilter gate, String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        gate.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void blocksUnenrolledUsersWhoMustUseTwoFactor() throws Exception {
        signIn(false);
        MockHttpServletResponse response = run(gate(true), "/iam/users");
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("TWO_FACTOR_ENROLLMENT_REQUIRED");
    }

    @Test
    void enrolmentAndSignOutPathsStayOpen() throws Exception {
        signIn(false);
        TwoFactorGateFilter gate = gate(true);
        assertThat(run(gate, "/users/me/two-factor/setup").getStatus()).isEqualTo(200);
        assertThat(run(gate, "/users/me").getStatus()).isEqualTo(200);
        assertThat(run(gate, "/auth/logout").getStatus()).isEqualTo(200);
        assertThat(run(gate, "/users/medic").getStatus()).isEqualTo(403);
    }

    @Test
    void enrolledOrExemptUsersPass() throws Exception {
        signIn(true);
        assertThat(run(gate(true), "/iam/users").getStatus()).isEqualTo(200);
        signIn(false);
        assertThat(run(gate(false), "/iam/users").getStatus()).isEqualTo(200);
    }

    @Test
    void anonymousRequestsAreLeftToTheSecurityChain() throws Exception {
        assertThat(run(gate(true), "/iam/users").getStatus()).isEqualTo(200);
    }
}
