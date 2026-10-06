package com.ninsky.cronos.iam.signin;

import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.List;
import java.util.stream.Stream;

/** Contract §8.1: everything a not-yet-enrolled user needs to enrol stays reachable. */
public final class TwoFactorGateAllowlist {

    private static final PathPatternRequestMatcher.Builder PATHS = PathPatternRequestMatcher.withDefaults();

    /** The contract's table, in order. */
    public static final List<RequestMatcher> ENROLMENT = List.of(
            PATHS.matcher(HttpMethod.GET, "/users/me/two-factor"),
            PATHS.matcher(HttpMethod.POST, "/users/me/two-factor/enrollment"),
            PATHS.matcher(HttpMethod.POST, "/users/me/two-factor/enrollment/confirm"),
            PATHS.matcher(HttpMethod.POST, "/auth/refresh"),
            PATHS.matcher(HttpMethod.POST, "/auth/logout"),
            PATHS.matcher(HttpMethod.GET, "/users/me"),
            PATHS.matcher(HttpMethod.GET, "/auth/sessions"),
            PATHS.matcher(HttpMethod.GET, "/auth/login-history"),
            PATHS.matcher(HttpMethod.GET, "/finance/settings"),
            PATHS.matcher(HttpMethod.GET, "/finance/*/catalog"));

    /** Unauthenticated routes: a stale token sent along must not turn them into 403s. */
    public static final List<RequestMatcher> PUBLIC = List.of(
            PATHS.matcher(HttpMethod.POST, "/auth/login"),
            PATHS.matcher(HttpMethod.POST, "/auth/register"),
            PATHS.matcher(HttpMethod.POST, "/auth/forgot-password"),
            PATHS.matcher(HttpMethod.POST, "/auth/reset-password"),
            PATHS.matcher(HttpMethod.POST, "/auth/activate"),
            PATHS.matcher("/error"),
            PATHS.matcher("/public/**"));

    public static final RequestMatcher ALL = new OrRequestMatcher(
            Stream.concat(ENROLMENT.stream(), PUBLIC.stream()).toList());

    private TwoFactorGateAllowlist() {
    }
}
