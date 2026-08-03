package com.ninsky.cronos.infrastructure.security;

import com.ninsky.cronos.infrastructure.persistence.auth.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "users", key = "#loginId")
    public UserDetails loadUserByUsername(String loginId) throws UsernameNotFoundException {
        // Como 'User' ya implementa 'UserDetails', simplemente la devolvemos.
        // Esto mantiene el contexto (UUID, 2FA, etc) vivo en todo el hilo de ejecución.
        return userRepository.findByUsernameWithRoles(loginId)
                .orElseGet(() -> userRepository.findByEmailWithRoles(loginId)
                        .orElseThrow(() -> new UsernameNotFoundException("User not found: " + loginId)));
    }
}