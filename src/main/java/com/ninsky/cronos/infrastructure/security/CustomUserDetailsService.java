package com.ninsky.cronos.infrastructure.security;

import com.ninsky.cronos.domain.port.auth.UserAuthLookupPort;
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

    @Override
    public UserDetails loadUserByUsername(String loginId) throws UsernameNotFoundException {
        return userAuthLookupPort.findByUsernameOrEmail(loginId)
                .map(CronosUserPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + loginId));
    }

    /** Token-authentication path: resolve by immutable id so renaming a user never invalidates their sessions. */
    public CronosUserPrincipal loadUserById(UUID userId) throws UsernameNotFoundException {
        return userAuthLookupPort.findById(userId)
                .map(CronosUserPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userId));
    }
}
