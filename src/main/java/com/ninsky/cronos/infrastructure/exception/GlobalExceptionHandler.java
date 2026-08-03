package com.ninsky.cronos.infrastructure.exception;

import com.ninsky.cronos.application.response.envelope.ApiError;
import com.ninsky.cronos.application.response.envelope.ApiResponseEnvelope;
import com.ninsky.cronos.domain.port.ErrorCatalogEntry;
import com.ninsky.cronos.domain.port.ErrorCatalogPort;
import com.ninsky.cronos.infrastructure.web.RequestLocaleResolver;
import com.ninsky.cronos.infrastructure.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Single place that turns any exception into the uniform {@link ApiResponseEnvelope} shape.
 * The {@link com.ninsky.cronos.infrastructure.aop.ErrorInterceptorAspect} enriches MDC with
 * category/timing diagnostics before the exception reaches here; this class is only responsible
 * for building the bilingual HTTP response from the {@link ErrorCatalogPort}.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final ErrorCatalogPort errorCatalogPort;
    private final MessageSource messageSource;

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponseEnvelope<Void>> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Locale locale = RequestLocaleResolver.resolve(request);
        List<ApiError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError("VALIDATION_FIELD_ERROR", resolveFieldMessage(fe, locale), fe.getField()))
                .collect(Collectors.toList());
        return respond(ErrorCodes.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, request, fieldErrors);
    }

    /**
     * Resolves a {@code {bundle.key}}-shaped Jakarta validation message against the app's bilingual
     * {@link MessageSource} directly, sidestepping Bean Validation's own interpolator wiring (Spring
     * MVC builds a separate throwaway validator for {@code @RequestBody} arguments by default, so
     * relying on that chain to reach our {@code MessageSource} proved unreliable in practice).
     */
    private String resolveFieldMessage(FieldError fe, Locale locale) {
        String raw = fe.getDefaultMessage();
        if (raw != null && raw.startsWith("{") && raw.endsWith("}")) {
            String key = raw.substring(1, raw.length() - 1);
            return messageSource.getMessage(key, null, raw, locale);
        }
        return raw;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponseEnvelope<Void>> handleResourceNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return respond(ErrorCodes.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, request, ex.getMessage());
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiResponseEnvelope<Void>> handleDuplicateResource(DuplicateResourceException ex, HttpServletRequest request) {
        return respond(ErrorCodes.DUPLICATE_RESOURCE, HttpStatus.CONFLICT, request, ex.getMessage());
    }

    @ExceptionHandler(SystemResourceException.class)
    public ResponseEntity<ApiResponseEnvelope<Void>> handleSystemResourceException(SystemResourceException ex, HttpServletRequest request) {
        return respond(ErrorCodes.SYSTEM_RESOURCE_CONFLICT, HttpStatus.CONFLICT, request, ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponseEnvelope<Void>> handleDataIntegrityViolation(DataIntegrityViolationException ex, HttpServletRequest request) {
        return respond(ErrorCodes.DATA_INTEGRITY_VIOLATION, HttpStatus.CONFLICT, request, ex.getMessage());
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponseEnvelope<Void>> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        return respond(ErrorCodes.BUSINESS_CONFLICT, HttpStatus.CONFLICT, request, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponseEnvelope<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String type = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown";
        String detail = "'%s' -> '%s' (%s)".formatted(ex.getName(), ex.getValue(), type);
        return respond(ErrorCodes.TYPE_MISMATCH, HttpStatus.BAD_REQUEST, request, detail);
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ApiResponseEnvelope<Void>> handleInvalidToken(InvalidTokenException ex, HttpServletRequest request) {
        return respond(ErrorCodes.INVALID_TOKEN, HttpStatus.UNAUTHORIZED, request, ex.getMessage());
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiResponseEnvelope<Void>> handleUserNotFound(UserNotFoundException ex, HttpServletRequest request) {
        return respond(ErrorCodes.USER_NOT_FOUND, HttpStatus.NOT_FOUND, request, ex.getMessage());
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiResponseEnvelope<Void>> handleValidationException(ValidationException ex, HttpServletRequest request) {
        List<ApiError> errors = ex.getDetails() == null
                ? List.of(new ApiError(ErrorCodes.VALIDATION_FAILED, ex.getMessage()))
                : ex.getDetails().stream().map(d -> new ApiError(ErrorCodes.VALIDATION_FAILED, d)).collect(Collectors.toList());
        return respond(ErrorCodes.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, request, errors);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiResponseEnvelope<Void>> handleRateLimitExceeded(RateLimitExceededException ex, HttpServletRequest request) {
        return respond(ErrorCodes.RATE_LIMIT_EXCEEDED, HttpStatus.TOO_MANY_REQUESTS, request, ex.getMessage());
    }

    @ExceptionHandler({BadCredentialsException.class, AuthenticationException.class})
    public ResponseEntity<ApiResponseEnvelope<Void>> handleAuthenticationException(Exception ex, HttpServletRequest request) {
        log.warn("Authentication error on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return respond(ErrorCodes.AUTHENTICATION_FAILED, HttpStatus.UNAUTHORIZED, request, (String) null);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponseEnvelope<Void>> handleNoHandlerFound(NoHandlerFoundException ex, HttpServletRequest request) {
        return respond(ErrorCodes.ROUTE_NOT_FOUND, HttpStatus.NOT_FOUND, request, (String) null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponseEnvelope<Void>> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error handling {} {}", request.getMethod(), request.getRequestURI(), ex);
        return respond(ErrorCodes.UNEXPECTED_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, request, (String) null);
    }

    // -- response construction --

    private ResponseEntity<ApiResponseEnvelope<Void>> respond(String errorCode, HttpStatus fallbackStatus,
                                                                HttpServletRequest request, String detailMessage) {
        List<ApiError> errors = detailMessage == null ? List.of() : List.of(new ApiError(errorCode, detailMessage));
        return respond(errorCode, fallbackStatus, request, errors);
    }

    private ResponseEntity<ApiResponseEnvelope<Void>> respond(String errorCode, HttpStatus fallbackStatus,
                                                                HttpServletRequest request, List<ApiError> errors) {
        Locale locale = RequestLocaleResolver.resolve(request);
        String traceId = MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY);

        var catalogEntry = errorCatalogPort.findByCode(errorCode);
        HttpStatus status = catalogEntry.map(e -> HttpStatus.valueOf(e.httpStatus())).orElse(fallbackStatus);
        String message = catalogEntry.map(e -> e.title(locale)).orElse(errorCode);
        List<ApiError> enrichedErrors = catalogEntry.map(e -> enrichWithCatalogDetail(errors, e, locale)).orElse(errors);

        return ResponseEntity.status(status).body(ApiResponseEnvelope.error(traceId, message, enrichedErrors));
    }

    /** For errors with no exception-specific detail (404, 500), fall back to the catalog's description + image. */
    private List<ApiError> enrichWithCatalogDetail(List<ApiError> errors, ErrorCatalogEntry entry, Locale locale) {
        if (!errors.isEmpty() || entry.imageUrl() == null) {
            return errors;
        }
        return List.of(new ApiError(entry.errorCode(), entry.description(locale), null, entry.imageUrl()));
    }
}
