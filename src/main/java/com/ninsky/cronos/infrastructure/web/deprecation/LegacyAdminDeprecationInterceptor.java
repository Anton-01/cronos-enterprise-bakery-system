package com.ninsky.cronos.infrastructure.web.deprecation;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

/** Marks the legacy {@code /admin} endpoints as deprecated (RFC 9745 / RFC 8594) and points at their successors. */
@Component
public class LegacyAdminDeprecationInterceptor implements HandlerInterceptor {

    static final String SUNSET = "Thu, 01 Apr 2027 00:00:00 GMT";
    private static final Map<String, String> SUCCESSORS = Map.of(
            "/admin/users", "/iam/users",
            "/admin/roles", "/iam/roles",
            "/admin/audit-log", "/iam/audit-events");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        response.setHeader("Deprecation", "true");
        response.setHeader("Sunset", SUNSET);
        SUCCESSORS.entrySet().stream()
                .filter(e -> path.startsWith(e.getKey()))
                .findFirst()
                .ifPresent(e -> response.setHeader("Link",
                        "<" + request.getContextPath() + e.getValue() + ">; rel=\"successor-version\""));
        return true;
    }
}
