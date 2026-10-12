package com.ninsky.cronos.infrastructure.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ninsky.cronos.iam.policy.TwoFactorRequirement;
import com.ninsky.cronos.iam.signin.TwoFactorGateFilter;
import com.ninsky.cronos.identity.infrastructure.SessionLockCustomRepository;
import com.ninsky.cronos.identity.infrastructure.SessionLockGateFilter;
import com.ninsky.cronos.infrastructure.exception.StrictContractResponder;
import com.ninsky.cronos.infrastructure.security.CustomAuthenticationEntryPoint;
import com.ninsky.cronos.infrastructure.security.JwtAuthenticationFilter;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;
    private final TwoFactorGateFilter twoFactorGate;
    private final SessionLockGateFilter sessionLockGate;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter,
                          CustomAuthenticationEntryPoint customAuthenticationEntryPoint, TwoFactorRequirement twoFactorRequirement,
                          StrictContractResponder strictContractResponder, ObjectMapper objectMapper,
                          SessionLockCustomRepository sessionLocks,
                          @Value("${app.security.two-factor-gate.enabled:true}") boolean twoFactorGateEnabled) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.customAuthenticationEntryPoint = customAuthenticationEntryPoint;
        this.sessionLockGate = new SessionLockGateFilter(sessionLocks, strictContractResponder, objectMapper);
        this.twoFactorGate = twoFactorGateEnabled ? new TwoFactorGateFilter(twoFactorRequirement, strictContractResponder, objectMapper) : null;
    }

    /**
     * Reachable without a Bearer token (the SPA never sends one to these; see its public-endpoints.ts). Social sign-in
     * ({@code /oauth2/**}) was removed: it leaked tokens in the redirect URL.
     */
    public static final String[] PUBLIC_ENDPOINTS = {
            "/auth/login", "/auth/login/two-factor", "/auth/register", "/auth/refresh", "/auth/logout", "/auth/unlock",
            "/auth/password-policy", "/auth/availability", "/auth/email/verify", "/auth/email/verify/resend",
            "/auth/password/forgot", "/auth/password/reset", "/auth/password/reset/*",
            "/auth/forgot-password", "/auth/reset-password", "/auth/activate",
            "/notifications/push/subscriptions/renew", "/notifications/stream",
            "/error", "/public/**", "/security/jwe-public-key", "swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/actuator/**"};

    public static final String[] DEFAULT_ALLOWED_METHODS_HTTP = {"GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"};
    public static final String[] DEFAULT_ALLOWED_ORIGINS = {"http://localhost:3000", "http://localhost:4200"};

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable).cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(auth -> auth
                        // Streamed downloads resume on an ASYNC dispatch of a request already authorised.
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .anyRequest().authenticated()
                ).exceptionHandling(exc -> exc
                        .authenticationEntryPoint(customAuthenticationEntryPoint)
                ).sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                ).addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterAfter(sessionLockGate, JwtAuthenticationFilter.class);
        if (twoFactorGate != null) {
            http.addFilterAfter(twoFactorGate, SessionLockGateFilter.class);
        }
        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(DEFAULT_ALLOWED_ORIGINS));
        configuration.setAllowedMethods(Arrays.asList(DEFAULT_ALLOWED_METHODS_HTTP));
        configuration.setAllowedHeaders(List.of("*"));
        // Readable by the SPA: ETag (If-Match on account-settings PUTs), Retry-After (429s), trace id,
        // Content-Disposition (file name of downloaded .xlsx import templates), Server-Timing (server time per request).
        configuration.setExposedHeaders(List.of("ETag", "Retry-After", "X-Trace-Id", "X-Correlation-Id", "Content-Disposition", "Server-Timing"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
