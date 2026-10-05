package com.ninsky.cronos.iam;

import com.ninsky.cronos.domain.port.ErrorCatalogPort;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

import java.util.Optional;

/** Stands in for the DB-backed error catalog and SecurityConfig's JWT chain; enables {@code @PreAuthorize}. */
@TestConfiguration
@EnableMethodSecurity
class IamWebMvcTestConfig {

    @Bean
    ErrorCatalogPort iamTestErrorCatalog() {
        return code -> Optional.empty();
    }

    @Bean
    SecurityFilterChain iamTestSecurity(HttpSecurity http) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .build();
    }
}
