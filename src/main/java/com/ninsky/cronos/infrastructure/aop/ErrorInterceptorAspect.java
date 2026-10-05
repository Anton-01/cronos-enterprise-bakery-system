package com.ninsky.cronos.infrastructure.aop;

import com.ninsky.cronos.domain.port.ErrorCatalogPort;
import com.ninsky.cronos.infrastructure.exception.BadCredentialsException;
import com.ninsky.cronos.infrastructure.exception.BusinessException;
import com.ninsky.cronos.infrastructure.exception.DataIntegrityViolationException;
import com.ninsky.cronos.infrastructure.exception.DuplicateResourceException;
import com.ninsky.cronos.infrastructure.exception.ErrorCodes;
import com.ninsky.cronos.infrastructure.exception.InvalidTokenException;
import com.ninsky.cronos.infrastructure.exception.RateLimitExceededException;
import com.ninsky.cronos.infrastructure.exception.ResourceNotFoundException;
import com.ninsky.cronos.infrastructure.exception.SystemResourceException;
import com.ninsky.cronos.infrastructure.exception.UserNotFoundException;
import com.ninsky.cronos.infrastructure.exception.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.MDC;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Intercepts exceptions thrown across the application/infrastructure service boundary,
 * categorizes them (SECURITY / FINANCIAL_BUSINESS / SYSTEM_TECHNICAL / VALIDATION — sourced from
 * {@code catalog_statuses}, not a hardcoded enum) and enriches MDC with diagnostic footprints
 * before rethrowing unchanged. {@link com.ninsky.cronos.infrastructure.exception.GlobalExceptionHandler}
 * remains the single place that builds the HTTP response — this aspect never handles the
 * exception, only observes it.
 */
@Aspect
@Component
@Slf4j
public class ErrorInterceptorAspect {

    private static final String DEFAULT_CATEGORY = "SYSTEM_TECHNICAL";

    private static final Map<Class<? extends Throwable>, String> EXCEPTION_ERROR_CODES = Map.ofEntries(
            Map.entry(ResourceNotFoundException.class, ErrorCodes.RESOURCE_NOT_FOUND),
            Map.entry(UserNotFoundException.class, ErrorCodes.USER_NOT_FOUND),
            Map.entry(DuplicateResourceException.class, ErrorCodes.DUPLICATE_RESOURCE),
            Map.entry(SystemResourceException.class, ErrorCodes.SYSTEM_RESOURCE_CONFLICT),
            Map.entry(DataIntegrityViolationException.class, ErrorCodes.DATA_INTEGRITY_VIOLATION),
            Map.entry(BusinessException.class, ErrorCodes.BUSINESS_CONFLICT),
            Map.entry(InvalidTokenException.class, ErrorCodes.INVALID_TOKEN),
            Map.entry(ValidationException.class, ErrorCodes.VALIDATION_FAILED),
            Map.entry(RateLimitExceededException.class, ErrorCodes.RATE_LIMIT_EXCEEDED),
            Map.entry(BadCredentialsException.class, ErrorCodes.AUTHENTICATION_FAILED),
            Map.entry(AuthenticationServiceException.class, ErrorCodes.AUTHENTICATION_FAILED)
    );

    private final ErrorCatalogPort errorCatalogPort;

    /** Lazy: the aspect is built while Flyway's Java migrations are wired, before any JdbcTemplate may exist. */
    public ErrorInterceptorAspect(@Lazy ErrorCatalogPort errorCatalogPort) {
        this.errorCatalogPort = errorCatalogPort;
    }

    // Filter beans are excluded: CGLIB-proxying a jakarta.servlet.Filter breaks GenericFilterBean's
    // final init(FilterConfig) lifecycle method (its `logger` field never gets initialized on the
    // proxy, NPE-ing Tomcat's filter startup) — advising filters brings no value here anyway, since
    // GlobalExceptionHandler is what actually needs the categorization, and filter-thrown exceptions
    // never reach it.
    @Around("(execution(* com.ninsky.cronos.application.service..*(..)) "
            + "|| execution(* com.ninsky.cronos.infrastructure..*(..))) "
            + "&& !within(jakarta.servlet.Filter+)")
    public Object intercept(ProceedingJoinPoint joinPoint) throws Throwable {
        long startNanos = System.nanoTime();
        try {
            return joinPoint.proceed();
        } catch (Throwable ex) {
            enrichMdc(joinPoint, ex, (System.nanoTime() - startNanos) / 1_000_000);
            throw ex;
        }
    }

    private void enrichMdc(ProceedingJoinPoint joinPoint, Throwable ex, long elapsedMs) {
        String errorCode = EXCEPTION_ERROR_CODES.get(ex.getClass());
        String category = (errorCode != null)
                ? errorCatalogPort.findByCode(errorCode).map(e -> e.category()).orElse(DEFAULT_CATEGORY)
                : DEFAULT_CATEGORY;

        MDC.put(ErrorMdcKeys.ERROR_CATEGORY, category);
        MDC.put(ErrorMdcKeys.ERROR_CODE, errorCode != null ? errorCode : ex.getClass().getSimpleName());
        MDC.put(ErrorMdcKeys.EXECUTION_TIME_MS, String.valueOf(elapsedMs));
        MDC.put(ErrorMdcKeys.FAILED_METHOD, joinPoint.getSignature().toShortString());

        log.warn("[{}] {} failed after {}ms: {}", category, joinPoint.getSignature().toShortString(), elapsedMs, ex.getMessage());
    }
}
