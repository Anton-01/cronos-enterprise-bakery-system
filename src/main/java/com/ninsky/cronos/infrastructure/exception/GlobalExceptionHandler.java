package com.ninsky.cronos.infrastructure.exception;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.ninsky.cronos.account.shared.api.AccountErrorMapper;
import com.ninsky.cronos.account.shared.domain.AccountDomainError;
import com.ninsky.cronos.account.shared.domain.AccountDomainException;
import com.ninsky.cronos.account.shared.domain.DomainValidationException;
import com.ninsky.cronos.application.response.envelope.ApiError;
import com.ninsky.cronos.application.response.envelope.ApiResponseEnvelope;
import com.ninsky.cronos.domain.port.ErrorCatalogEntry;
import com.ninsky.cronos.domain.port.ErrorCatalogPort;
import com.ninsky.cronos.infrastructure.web.RequestLocaleResolver;
import com.ninsky.cronos.infrastructure.web.TraceIdFilter;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
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
    private final AccountErrorMapper accountErrorMapper;
    private final StrictContractResponder strict;
    private final ObjectProvider<AccessDeniedRecorder> accessDeniedRecorder;

    private static final String VALIDATION_ERROR_IMAGE_URL = "/assets/errors/validation.svg";

    /** "Required" constraints win over shape/size ones when a field fails several at once. */
    private static final Set<String> PRIMARY_CONSTRAINTS = Set.of("NotNull", "NotBlank", "NotEmpty");

    /**
     * Every field error at once (never fail-fast), one per field: sorted deterministically (field,
     * then required-before-shape, then constraint name) because Hibernate Validator hands violations
     * over in hash order, then de-duplicated into an insertion-ordered map so the first error per
     * field wins. {@code field} is the request-body JSON path ({@code address.zipCode}).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponseEnvelope<Void>> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Locale locale = RequestLocaleResolver.resolve(request);
        List<ApiError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparing(FieldError::getField)
                        .thenComparing(fe -> PRIMARY_CONSTRAINTS.contains(fe.getCode()) ? 0 : 1)
                        .thenComparing(fe -> Objects.toString(fe.getCode(), "")))
                .map(fe -> new ApiError(AccountDomainError.VALIDATION_FIELD_ERROR, resolveFieldMessage(fe, locale), fe.getField(), VALIDATION_ERROR_IMAGE_URL))
                .collect(Collectors.toMap(ApiError::field, Function.identity(), (first, ignored) -> first, LinkedHashMap::new))
                .sequencedValues().stream().toList();
        if (strict.applies(request)) {
            return strict.respondLocalized(ApiErrorCode.VALIDATION_ERROR, fieldErrors, request);
        }
        return respond(ErrorCodes.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, request, fieldErrors);
    }

    /** IAM/Finance business failures: every violation, localised, with its own stable code. */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponseEnvelope<Void>> handleApiException(ApiException ex, HttpServletRequest request) {
        if (ex.primaryCode() == ApiErrorCode.PRIVILEGE_ESCALATION || ex.primaryCode() == ApiErrorCode.ACCESS_DENIED) {
            accessDeniedRecorder.ifAvailable(recorder -> recorder.record(request, ex.primaryCode().name()));
        }
        return strict.respond(ex, request);
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

    /**
     * Malformed/unparseable request body (bad JSON, wrong type for a field, etc.) — arrives before
     * Bean Validation even runs, so {@link MethodArgumentNotValidException} never fires for this
     * case. Previously fell through to {@link #handleUnexpected}, i.e. a client mistake surfaced
     * as a 500.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponseEnvelope<Void>> handleUnreadableBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        UnrecognizedPropertyException unknown = findCause(ex, UnrecognizedPropertyException.class);
        if (unknown != null) {
            // Only reachable for @RejectUnknownProperties DTOs (mass-assignment guard on account endpoints).
            String field = jsonPath(unknown.getPath());
            String message = messageSource.getMessage("account.validation.unknownProperty", new Object[]{field},
                    "Property not allowed", RequestLocaleResolver.resolve(request));
            return respond(ErrorCodes.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, request,
                    List.of(new ApiError(AccountDomainError.VALIDATION_ERROR, message, field)));
        }
        if (strict.applies(request)) {
            InvalidFormatException invalid = findCause(ex, InvalidFormatException.class);
            String field = invalid == null ? null : jsonPath(invalid.getPath());
            return strict.respond(ApiErrorCode.VALIDATION_ERROR, field, field == null ? "api.validation.malformedBody" : "api.validation.invalidValue", request);
        }
        return respond(ErrorCodes.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, request, "Malformed or missing request body");
    }

    private static <T extends Throwable> T findCause(Throwable ex, Class<T> type) {
        for (Throwable cause = ex.getCause(); cause != null && cause != cause.getCause(); cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return type.cast(cause);
            }
        }
        return null;
    }

    private static String jsonPath(List<JsonMappingException.Reference> references) {
        StringBuilder path = new StringBuilder();
        for (JsonMappingException.Reference reference : references) {
            if (reference.getFieldName() != null) {
                if (!path.isEmpty()) {
                    path.append('.');
                }
                path.append(reference.getFieldName());
            } else if (reference.getIndex() >= 0) {
                path.append('[').append(reference.getIndex()).append(']');
            }
        }
        return path.toString();
    }

    /**
     * Bean Validation on {@code @RequestParam}/{@code @PathVariable} (requires {@code @Validated}
     * at the controller class level) — a different exception type than
     * {@link MethodArgumentNotValidException}, which only covers {@code @Valid @RequestBody}.
     * Previously fell through to {@link #handleUnexpected} (500) instead of a field-level 400.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponseEnvelope<Void>> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<ApiError> errors = ex.getConstraintViolations().stream()
                .sorted(Comparator.comparing((ConstraintViolation<?> cv) -> jsonPath(cv.getPropertyPath()))
                        .thenComparing(cv -> Objects.toString(cv.getMessage(), "")))
                .map(cv -> new ApiError(AccountDomainError.VALIDATION_FIELD_ERROR, cv.getMessage(), jsonPath(cv.getPropertyPath()), VALIDATION_ERROR_IMAGE_URL))
                .collect(Collectors.toMap(ApiError::field, Function.identity(), (first, ignored) -> first, LinkedHashMap::new))
                .sequencedValues().stream().toList();
        if (strict.applies(request)) {
            return strict.respondLocalized(ApiErrorCode.VALIDATION_ERROR, errors, request);
        }
        return respond(ErrorCodes.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, request, errors);
    }

    /**
     * Method-validation paths look like {@code upsert.request.address.zipCode}: drop the method and
     * parameter nodes and keep the bean-property path the client sent. A constraint on the parameter
     * itself (no property nodes) reports the parameter name, e.g. {@code file}.
     */
    static String jsonPath(Path path) {
        StringBuilder json = new StringBuilder();
        String parameterName = null;
        for (Path.Node node : path) {
            ElementKind kind = node.getKind();
            if (kind == ElementKind.METHOD || kind == ElementKind.CONSTRUCTOR || kind == ElementKind.RETURN_VALUE
                    || kind == ElementKind.CROSS_PARAMETER) {
                continue;
            }
            if (kind == ElementKind.PARAMETER) {
                parameterName = node.getName();
                continue;
            }
            if (node.getName() == null || node.getName().startsWith("<")) {
                continue;
            }
            if (node.getIndex() != null) {
                json.append('[').append(node.getIndex()).append(']');
            }
            if (!json.isEmpty()) {
                json.append('.');
            }
            json.append(node.getName());
        }
        return json.isEmpty() ? Objects.toString(parameterName, path.toString()) : json.toString();
    }

    // -- Account settings module --

    @ExceptionHandler(AccountDomainException.class)
    public ResponseEntity<ApiResponseEnvelope<Void>> handleAccountDomain(AccountDomainException ex, HttpServletRequest request) {
        AccountErrorMapper.MappedError mapped = accountErrorMapper.map(ex, RequestLocaleResolver.resolve(request));
        ResponseEntity<ApiResponseEnvelope<Void>> response = respondWithStatus(mapped.catalogCode(), mapped.status(), request, mapped.errors());
        if (mapped.retryAfterSeconds() == null) {
            return response;
        }
        return ResponseEntity.status(response.getStatusCode())
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(mapped.retryAfterSeconds()))
                .body(response.getBody());
    }

    /** A value object rejected input that Bean Validation should already have caught — still a client error, never a 500. */
    @ExceptionHandler(DomainValidationException.class)
    public ResponseEntity<ApiResponseEnvelope<Void>> handleDomainValidation(DomainValidationException ex, HttpServletRequest request) {
        String message = messageSource.getMessage(ex.messageKey(), ex.args().toArray(), ex.messageKey(), RequestLocaleResolver.resolve(request));
        return respond(ErrorCodes.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, request,
                List.of(new ApiError(AccountDomainError.VALIDATION_ERROR, message, null)));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponseEnvelope<Void>> handleMaxUploadSize(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        if (strict.applies(request)) {
            return strict.respond(ApiErrorCode.PAYLOAD_TOO_LARGE, "file", "api.upload.tooLarge", request);
        }
        String message = messageSource.getMessage("account.avatar.file.tooLarge", new Object[]{2}, "File too large", RequestLocaleResolver.resolve(request));
        return respondWithStatus(ErrorCodes.VALIDATION_FAILED, HttpStatus.PAYLOAD_TOO_LARGE, request,
                List.of(new ApiError(AccountDomainError.VALIDATION_FIELD_ERROR, message, "file")));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponseEnvelope<Void>> handleMissingPart(MissingServletRequestPartException ex, HttpServletRequest request) {
        if (strict.applies(request)) {
            return strict.respond(ApiErrorCode.VALIDATION_ERROR, ex.getRequestPartName(), "api.validation.required", request);
        }
        String message = messageSource.getMessage("account.avatar.file.required", null, "File is required", RequestLocaleResolver.resolve(request));
        return respond(ErrorCodes.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, request,
                List.of(new ApiError(AccountDomainError.VALIDATION_FIELD_ERROR, message, ex.getRequestPartName())));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponseEnvelope<Void>> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        if (strict.applies(request)) {
            return strict.respond(ApiErrorCode.UNSUPPORTED_MEDIA_TYPE, null, "api.upload.unsupportedMediaType", request);
        }
        String message = messageSource.getMessage("account.validation.unsupportedMediaType", null, "Unsupported media type", RequestLocaleResolver.resolve(request));
        return respondWithStatus(ErrorCodes.VALIDATION_FAILED, HttpStatus.UNSUPPORTED_MEDIA_TYPE, request,
                List.of(new ApiError(AccountDomainError.VALIDATION_ERROR, message, null)));
    }

    /** Lost update between our version check and the flush — someone else committed first. */
    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, OptimisticLockException.class})
    public ResponseEntity<ApiResponseEnvelope<Void>> handleOptimisticLock(Exception ex, HttpServletRequest request) {
        log.info("Optimistic lock conflict on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        if (strict.applies(request)) {
            return strict.respond(ApiException.concurrentModification(), request);
        }
        String message = messageSource.getMessage("account.concurrency.conflict", null, "Modified concurrently", RequestLocaleResolver.resolve(request));
        return respondWithStatus(ErrorCodes.SYSTEM_RESOURCE_CONFLICT, HttpStatus.CONFLICT, request,
                List.of(new ApiError(AccountDomainError.SYSTEM_RESOURCE_CONFLICT, message, null)));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponseEnvelope<Void>> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        accessDeniedRecorder.ifAvailable(recorder -> recorder.record(request, ApiErrorCode.ACCESS_DENIED.name()));
        if (strict.applies(request)) {
            return strict.respond(ApiErrorCode.ACCESS_DENIED, null, "api.accessDenied", request);
        }
        return respond(ErrorCodes.UNAUTHORIZED_MODIFICATION, HttpStatus.FORBIDDEN, request, (String) null);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponseEnvelope<Void>> handleResourceNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        if (strict.applies(request)) {
            return strict.respond(ApiErrorCode.RESOURCE_NOT_FOUND, null, "api.notFound", request);
        }
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

    @ExceptionHandler(UnauthorizedCategoryModificationException.class)
    public ResponseEntity<ApiResponseEnvelope<Void>> handleUnauthorizedCategoryModification(UnauthorizedCategoryModificationException ex, HttpServletRequest request) {
        return respond(ErrorCodes.UNAUTHORIZED_MODIFICATION, HttpStatus.FORBIDDEN, request, ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponseEnvelope<Void>> handleDataIntegrityViolation(DataIntegrityViolationException ex, HttpServletRequest request) {
        return respond(ErrorCodes.DATA_INTEGRITY_VIOLATION, HttpStatus.CONFLICT, request, ex.getMessage());
    }

    /** Unit catalog / import rule failures: message resolved from the i18n bundle in the caller's language. */
    @ExceptionHandler(CatalogException.class)
    public ResponseEntity<ApiResponseEnvelope<Void>> handleCatalog(CatalogException ex, HttpServletRequest request) {
        String message = messageSource.getMessage(ex.messageKey(), ex.args(), ex.messageKey(), RequestLocaleResolver.resolve(request));
        String errorCode = ex.reason().errorCode();
        HttpStatus fallbackStatus = switch (ex.reason()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INVALID -> HttpStatus.BAD_REQUEST;
            case DUPLICATE, INTEGRITY, BUSINESS_RULE -> HttpStatus.CONFLICT;
        };
        return respond(errorCode, fallbackStatus, request, List.of(new ApiError(errorCode, message, ex.field())));
    }

    /**
     * A database constraint (unique index, CHECK, FK) caught what an application-level check let
     * through — typically two concurrent writers racing past the same "exists?" check. A conflict
     * on the caller's input, not a server fault: 409 instead of the former 500. The constraint
     * detail stays in the log; the client never sees SQL.
     */
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponseEnvelope<Void>> handleConstraintRace(org.springframework.dao.DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Database constraint rejected {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        if (strict.applies(request)) {
            return strict.respond(constraintCode(ex), null, "api.constraint." + constraintCode(ex).name(), request);
        }
        return respond(ErrorCodes.DATA_INTEGRITY_VIOLATION, HttpStatus.CONFLICT, request, (String) null);
    }

    /** Catalog CSV refused as a whole: one localized error per problem, prefixed with its CSV line. */
    @ExceptionHandler(CsvImportRejectedException.class)
    public ResponseEntity<ApiResponseEnvelope<Void>> handleCsvImportRejected(CsvImportRejectedException ex, HttpServletRequest request) {
        Locale locale = RequestLocaleResolver.resolve(request);
        List<ApiError> errors = ex.errors().stream().map(error -> {
            String message = messageSource.getMessage(error.messageKey(), error.args().toArray(), error.messageKey(), locale);
            if (error.line() != null) {
                message = messageSource.getMessage("import.csv.line", new Object[]{error.line(), message}, message, locale);
            }
            return new ApiError(ErrorCodes.VALIDATION_FAILED, message, error.column());
        }).toList();
        return respond(ErrorCodes.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, request, errors);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponseEnvelope<Void>> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        return respond(ErrorCodes.BUSINESS_CONFLICT, HttpStatus.CONFLICT, request, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponseEnvelope<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        if (strict.applies(request)) {
            return strict.respond(ApiErrorCode.VALIDATION_ERROR, ex.getName(), "api.validation.invalidValue", request);
        }
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
        if (strict.applies(request)) {
            return strict.respond(ApiErrorCode.RATE_LIMITED, null, "api.rateLimited.generic", request);
        }
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
        if (strict.applies(request)) {
            return strict.respond(ApiErrorCode.INTERNAL_ERROR, null, "api.internalError", request);
        }
        return respond(ErrorCodes.UNEXPECTED_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, request, (String) null);
    }

    /** 23505 unique → duplicate; 23503 foreign key → still referenced; anything else → invalid input. */
    private static ApiErrorCode constraintCode(org.springframework.dao.DataIntegrityViolationException ex) {
        java.sql.SQLException sql = findCause(ex, java.sql.SQLException.class);
        String state = sql == null ? null : sql.getSQLState();
        if ("23505".equals(state)) {
            return ApiErrorCode.DUPLICATE_RESOURCE;
        }
        return "23503".equals(state) ? ApiErrorCode.RESOURCE_IN_USE : ApiErrorCode.VALIDATION_ERROR;
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

    /**
     * Like {@link #respond(String, HttpStatus, HttpServletRequest, List)} but the status is fixed by
     * the caller: the catalog only supplies the localized title (e.g. a 413/415 reuses the
     * "Validation Failed" title whose catalog row says 400).
     */
    private ResponseEntity<ApiResponseEnvelope<Void>> respondWithStatus(String catalogCode, HttpStatus status,
                                                                          HttpServletRequest request, List<ApiError> errors) {
        Locale locale = RequestLocaleResolver.resolve(request);
        String traceId = MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY);
        String message = errorCatalogPort.findByCode(catalogCode).map(e -> e.title(locale)).orElse(catalogCode);
        return ResponseEntity.status(status).body(ApiResponseEnvelope.error(traceId, message, errors));
    }

    /** For errors with no exception-specific detail (404, 500), fall back to the catalog's description + image. */
    private List<ApiError> enrichWithCatalogDetail(List<ApiError> errors, ErrorCatalogEntry entry, Locale locale) {
        if (!errors.isEmpty() || entry.imageUrl() == null) {
            return errors;
        }
        return List.of(new ApiError(entry.errorCode(), entry.description(locale), null, entry.imageUrl()));
    }
}
