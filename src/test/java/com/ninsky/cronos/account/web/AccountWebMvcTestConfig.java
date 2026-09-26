package com.ninsky.cronos.account.web;

import com.ninsky.cronos.domain.port.ErrorCatalogEntry;
import com.ninsky.cronos.domain.port.ErrorCatalogPort;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.validation.Validator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Map;
import java.util.Optional;

/**
 * Stands in for the DB-backed error catalog, WebMvcConfig's validator wiring and SecurityConfig's
 * JWT chain (same shape: stateless, CSRF off, /public/** open, 401 for anonymous).
 */
@TestConfiguration
class AccountWebMvcTestConfig {

    /** Catalog titles as seeded by V2__error_event_catalogs.sql (English/Spanish). */
    private static final Map<String, String[]> TITLES = Map.of(
            "VALIDATION_FAILED", new String[]{"Validation Failed", "Error de Validación", "400"},
            "DUPLICATE_RESOURCE", new String[]{"Duplicate Resource", "Recurso Duplicado", "409"},
            "SYSTEM_RESOURCE_CONFLICT", new String[]{"System Resource Conflict", "Conflicto de Recurso del Sistema", "409"},
            "RATE_LIMIT_EXCEEDED", new String[]{"Too Many Requests", "Demasiadas Solicitudes", "429"});

    @Bean
    ErrorCatalogPort errorCatalogPort() {
        return code -> Optional.ofNullable(TITLES.get(code))
                .map(t -> new ErrorCatalogEntry(code, "VALIDATION", Integer.parseInt(t[2]), null, t[0], t[1], null, null));
    }

    @Bean
    SecurityFilterChain accountTestSecurity(HttpSecurity http) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers("/public/**").permitAll().anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .build();
    }

    @Bean
    WebMvcConfigurer accountValidatorConfigurer(LocalValidatorFactoryBean localValidatorFactoryBean) {
        return new WebMvcConfigurer() {
            @Override
            public Validator getValidator() {
                return localValidatorFactoryBean;
            }
        };
    }
}
