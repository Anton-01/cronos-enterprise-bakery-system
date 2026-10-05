package com.ninsky.cronos.infrastructure.security;

import com.ninsky.cronos.domain.model.auth.AuthUserProjection;
import com.ninsky.cronos.domain.port.auth.UserAuthLookupPort;
import com.ninsky.cronos.iam.access.UserAccessService;
import com.ninsky.cronos.iam.access.UserAccessState;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserAuthLookupPort userAuthLookupPort;
    private final UserAccessService userAccessService;

    @Override
    public UserDetails loadUserByUsername(String loginId) throws UsernameNotFoundException {
        return userAuthLookupPort.findByUsernameOrEmail(loginId)
                .map(this::toPrincipal)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + loginId));
    }

    /** Token-authentication path: resolve by immutable id so renaming a user never invalidates their sessions. */
    public CronosUserPrincipal loadUserById(UUID userId) throws UsernameNotFoundException {
        return userAuthLookupPort.findById(userId)
                .map(this::toPrincipal)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userId));
    }

    /** Authorities come from the effective-permission resolver (spec §1.4.2). */
    private CronosUserPrincipal toPrincipal(AuthUserProjection projection) {
        UserAccessState access = userAccessService.current(projection.id());
        return new CronosUserPrincipal(projection, access.roleCodes(), access.permissionClaim(), access.superAdmin());
    }
}
