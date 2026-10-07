package com.ninsky.cronos.infrastructure.web.deprecation;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.Map;

/** Deprecates the pre-kitchen catalog/recipe endpoints (kitchen §13) for one release. */
@Component
public class LegacyKitchenDeprecationInterceptor implements HandlerInterceptor {

    /** Registered path patterns (Ant style). */
    public static final List<String> PATHS = List.of("/raw-material/**", "/allergen/**", "/recipes/*/ingredients/**",
            "/recipes/*/fixed-costs/**", "/recipes/*/cost", "/recipes/*/sync-costs");

    private static final Map<String, String> SUCCESSORS = Map.of(
            "/raw-material", "/ingredients",
            "/allergen", "/allergens",
            "/recipes", "/recipes");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        response.setHeader("Deprecation", "true");
        response.setHeader("Sunset", LegacyAdminDeprecationInterceptor.SUNSET);
        SUCCESSORS.entrySet().stream()
                .filter(e -> path.equals(e.getKey()) || path.startsWith(e.getKey() + "/"))
                .findFirst()
                .ifPresent(e -> response.setHeader("Link",
                        "<" + request.getContextPath() + e.getValue() + ">; rel=\"successor-version\""));
        return true;
    }
}
