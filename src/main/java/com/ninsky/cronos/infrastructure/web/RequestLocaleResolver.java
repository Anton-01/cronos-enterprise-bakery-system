package com.ninsky.cronos.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/**
 * Resolves the bilingual (en/es) response locale straight from the raw {@code Accept-Language}
 * header. Used anywhere that runs before Spring MVC's own locale resolution — e.g. the security
 * filter chain's {@code AuthenticationEntryPoint}, which executes ahead of DispatcherServlet —
 * so error responses stay consistently bilingual regardless of where the failure occurs.
 * Defaults to Spanish, matching the rest of the codebase (e.g. {@code UserProfile.language}).
 */
public final class RequestLocaleResolver {

    public static final Locale DEFAULT_LOCALE = Locale.of("es");

    private RequestLocaleResolver() {
    }

    public static Locale resolve(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.ACCEPT_LANGUAGE);
        if (!StringUtils.hasText(header)) {
            return DEFAULT_LOCALE;
        }
        try {
            List<Locale.LanguageRange> ranges = Locale.LanguageRange.parse(header);
            if (ranges.isEmpty()) {
                return DEFAULT_LOCALE;
            }
            String language = ranges.get(0).getRange();
            return language.toLowerCase(Locale.ROOT).startsWith("en") ? Locale.ENGLISH : DEFAULT_LOCALE;
        } catch (IllegalArgumentException ex) {
            return DEFAULT_LOCALE;
        }
    }
}
