package com.ninsky.cronos.finance.web;

import com.ninsky.cronos.domain.model.auth.AuthUserProjection;
import com.ninsky.cronos.domain.port.ErrorCatalogPort;
import com.ninsky.cronos.infrastructure.security.CronosUserPrincipal;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.validation.Validator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

/** Stands in for the DB error catalog, WebMvcConfig's validator and SecurityConfig's JWT chain. */
@TestConfiguration
@EnableMethodSecurity
public class FinanceWebMvcTestConfig {

    public static final UUID USER_ID = UUID.fromString("6d1f6f0e-2d55-4c1a-9a0c-3b8f4f6f2a11");

    /** An authenticated caller holding exactly these permission codes. */
    public static RequestPostProcessor withPermissions(String... permissions) {
        CronosUserPrincipal principal = new CronosUserPrincipal(new AuthUserProjection(USER_ID, "finance", "f@cronos.test", "x",
                true, true, true, true, false, null, Set.of("USER"), Set.of()), Set.of("USER"), Set.of(permissions), false);
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @Bean
    ErrorCatalogPort errorCatalogPort() {
        return code -> Optional.empty();
    }

    @Bean
    SecurityFilterChain financeTestSecurity(HttpSecurity http) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .build();
    }

    @Bean
    WebMvcConfigurer financeValidatorConfigurer(LocalValidatorFactoryBean localValidatorFactoryBean) {
        return new WebMvcConfigurer() {
            @Override
            public Validator getValidator() {
                return localValidatorFactoryBean;
            }
        };
    }
}
