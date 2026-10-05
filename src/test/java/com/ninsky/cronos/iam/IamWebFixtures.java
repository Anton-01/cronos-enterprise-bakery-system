package com.ninsky.cronos.iam;

import com.ninsky.cronos.domain.model.auth.AuthUserProjection;
import com.ninsky.cronos.infrastructure.security.CronosUserPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Set;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

/** Authenticated principals for IAM web slices. */
public final class IamWebFixtures {

    public static final UUID USER_ID = UUID.fromString("6d1f6f0e-2d55-4c1a-9a0c-3b8f4f6f2a11");

    private IamWebFixtures() {
    }

    public static RequestPostProcessor withPermissions(String... permissions) {
        CronosUserPrincipal principal = new CronosUserPrincipal(new AuthUserProjection(USER_ID, "admin", "a@b.c", "x",
                true, true, true, true, false, null, Set.of("USER"), Set.of(permissions)));
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
