package com.ninsky.cronos.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

import java.io.InputStream;

/**
 * Conditional GET on reference catalogs (performance-latency P1-1, contract addition §5.3): the response carries an
 * {@code ETag} (hash of the body) and {@code Cache-Control: private, max-age=60}, and a matching {@code If-None-Match}
 * is answered {@code 304 Not Modified} without a body. The payload itself comes from the services' 60-second caches,
 * so a revalidation costs no database round trip either. Registered after the security chain: only authenticated
 * responses are hashed, and an error response is never cached by the browser (only 2xx get the header).
 */
public class CatalogConditionalGetFilter extends ShallowEtagHeaderFilter {

    /** Servlet paths (below {@code /api/v1}) of the catalogs. */
    static final String[] URL_PATTERNS = {
            "/measurement-unit/catalog", "/unit-type/catalog",
            "/category", "/category/system",
            "/allergens", "/allergens/*", "/allergen", "/allergen/system",
            "/recipe-sections",
            "/finance/currencies/catalog", "/finance/tax-rates/catalog"};

    static final String CACHE_CONTROL = "private, max-age=60";

    /** 2xx GETs get the hash and, then, the cache header; errors are never marked cacheable. */
    @Override
    protected boolean isEligibleForEtag(HttpServletRequest request, HttpServletResponse response, int responseStatusCode,
                                        InputStream inputStream) {
        boolean eligible = super.isEligibleForEtag(request, response, responseStatusCode, inputStream);
        if (eligible) {
            // Spring Security's cache-control writer runs later and leaves an existing header alone.
            response.setHeader(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL);
        }
        return eligible;
    }

    /** Registration limited to {@link #URL_PATTERNS}. */
    @Configuration(proxyBeanMethods = false)
    static class Registration {

        @Bean
        FilterRegistrationBean<CatalogConditionalGetFilter> catalogConditionalGetFilter() {
            FilterRegistrationBean<CatalogConditionalGetFilter> registration = new FilterRegistrationBean<>(new CatalogConditionalGetFilter());
            registration.addUrlPatterns(URL_PATTERNS);
            registration.setName("catalogConditionalGetFilter");
            return registration;
        }
    }
}
