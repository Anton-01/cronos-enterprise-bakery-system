package com.ninsky.cronos.account.shared.infrastructure;

import com.ninsky.cronos.account.shared.infrastructure.security.SecurityContextCurrentUserProvider;
import com.ninsky.cronos.domain.model.auth.AuthUserProjection;
import com.ninsky.cronos.infrastructure.security.CronosUserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityContextCurrentUserProviderTest {

    private final SecurityContextCurrentUserProvider provider = new SecurityContextCurrentUserProvider();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsTheAuthenticatedPrincipalsImmutableUserId() {
        UUID id = UUID.randomUUID();
        var principal = new CronosUserPrincipal(new AuthUserProjection(id, "renamed_user", "a@b.c", "x", true, true, true, true,
                false, null, Set.of("BAKER"), Set.of()));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        assertThat(provider.currentUserId()).isEqualTo(id);
    }

    @Test
    void anonymousOrForeignPrincipalsAreRejected() {
        assertThatThrownBy(provider::currentUserId).isInstanceOf(AuthenticationCredentialsNotFoundException.class);

        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("k", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        assertThatThrownBy(provider::currentUserId).isInstanceOf(AuthenticationCredentialsNotFoundException.class);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("someone", null, AuthorityUtils.NO_AUTHORITIES));
        assertThatThrownBy(provider::currentUserId).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }
}
