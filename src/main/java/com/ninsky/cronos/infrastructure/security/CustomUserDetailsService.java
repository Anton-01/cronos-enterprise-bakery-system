package com.ninsky.cronos.infrastructure.security;

import com.ninsky.cronos.domain.port.auth.UserAuthLookupPort;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

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
}
