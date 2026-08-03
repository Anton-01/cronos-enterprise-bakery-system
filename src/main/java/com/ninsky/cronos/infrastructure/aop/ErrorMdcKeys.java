package com.ninsky.cronos.infrastructure.aop;

/** MDC keys written by {@link ErrorInterceptorAspect}; cleared by TraceIdFilter after each request. */
public final class ErrorMdcKeys {

    public static final String ERROR_CATEGORY = "errorCategory";
    public static final String ERROR_CODE = "errorCode";
    public static final String EXECUTION_TIME_MS = "executionTimeMs";
    public static final String FAILED_METHOD = "failedMethod";

    private ErrorMdcKeys() {
    }
}
