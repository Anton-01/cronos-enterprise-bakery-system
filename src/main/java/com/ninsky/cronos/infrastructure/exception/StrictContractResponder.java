package com.ninsky.cronos.infrastructure.exception;

import com.ninsky.cronos.application.response.envelope.ApiError;
import com.ninsky.cronos.application.response.envelope.ApiResponseEnvelope;
import com.ninsky.cronos.infrastructure.web.RequestLocaleResolver;
import com.ninsky.cronos.infrastructure.web.StrictApiContract;
import com.ninsky.cronos.infrastructure.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.context.MessageSource;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import java.util.List;
import java.util.Locale;

/** Builds error envelopes for {@link StrictApiContract} controllers: stable codes, localised messages. */
@Component
public class StrictContractResponder {

    private final MessageSource messageSource;

    public StrictContractResponder(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /** True when the request was routed to a controller annotated with {@link StrictApiContract}. */
    public boolean applies(HttpServletRequest request) {
        return request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE) instanceof HandlerMethod handler
                && AnnotatedElementUtils.hasAnnotation(handler.getBeanType(), StrictApiContract.class);
    }

    public ResponseEntity<ApiResponseEnvelope<Void>> respond(ApiException ex, HttpServletRequest request) {
        Locale locale = RequestLocaleResolver.resolve(request);
        List<ApiError> errors = ex.violations().stream()
                .map(v -> new ApiError(v.code().name(), message(v.messageKey(), v.args().toArray(), locale), v.field()))
                .toList();
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(ex.primaryCode().status());
        if (ex.retryAfterSeconds() != null) {
            builder.header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.retryAfterSeconds()));
        }
        return builder.body(ApiResponseEnvelope.error(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY), title(ex.primaryCode(), locale), errors));
    }

    public ResponseEntity<ApiResponseEnvelope<Void>> respond(ApiErrorCode code, String field, String messageKey, HttpServletRequest request) {
        return respond(ApiException.of(code, field, messageKey), request);
    }

    /** Already-localised errors (Bean Validation output), re-coded as {@code code}. */
    public ResponseEntity<ApiResponseEnvelope<Void>> respondLocalized(ApiErrorCode code, List<ApiError> localized, HttpServletRequest request) {
        Locale locale = RequestLocaleResolver.resolve(request);
        List<ApiError> errors = localized.stream().map(e -> new ApiError(code.name(), e.message(), e.field())).toList();
        return ResponseEntity.status(code.status())
                .body(ApiResponseEnvelope.error(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY), title(code, locale), errors));
    }

    private String title(ApiErrorCode code, Locale locale) {
        return message(code.titleKey(), null, locale);
    }

    private String message(String key, Object[] args, Locale locale) {
        return messageSource.getMessage(key, args, key, locale);
    }
}
