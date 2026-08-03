package com.ninsky.cronos.infrastructure.config.security.handler;

import com.ninsky.cronos.application.response.auth.LoginResponse;
import com.ninsky.cronos.application.service.auth.AuthenticationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Slf4j
@Component
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final AuthenticationService authenticationService;

    @Value("${app.frontend.oauth2-redirect-uri:http://localhost:4200/oauth2-callback}")
    private String frontendRedirectUri;

    public OAuth2LoginSuccessHandler(@Lazy AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {

        OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
        String provider = token.getAuthorizedClientRegistrationId(); // Ej: "google"
        OAuth2User oAuth2User = token.getPrincipal();

        log.info("OAuth2 Login successful from provider: {}", provider);

        try {
            // 1. Delegamos al AuthenticationService para que haga todo el trabajo pesado
            // (Verificar BD, crear usuario si no existe, Fingerprinting, Sesiones y Tokens)
            LoginResponse loginResponse = authenticationService.processOAuth2Login(oAuth2User, provider);

            // 2. Construimos la URL hacia Angular con los tokens como parámetros
            String targetUrl = UriComponentsBuilder.fromUriString(frontendRedirectUri)
                    .queryParam("accessToken", loginResponse.accessToken())
                    .queryParam("refreshToken", loginResponse.refreshToken())
                    .build().toUriString();

            // 3. Redirigimos al usuario al Frontend
            getRedirectStrategy().sendRedirect(request, response, targetUrl);

        } catch (Exception e) {
            log.error("Error processing OAuth2 login", e);
            // Si falla algo (ej. cuenta bloqueada), mandamos al frontend con un error
            String errorUrl = UriComponentsBuilder.fromUriString(frontendRedirectUri)
                    .queryParam("error", "Authentication failed").build().toUriString();
            getRedirectStrategy().sendRedirect(request, response, errorUrl);
        }
    }
}
