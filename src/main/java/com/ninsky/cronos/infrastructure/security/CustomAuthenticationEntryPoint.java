package com.ninsky.cronos.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ninsky.cronos.application.response.envelope.ApiError;
import com.ninsky.cronos.application.response.envelope.ApiResponseEnvelope;
import com.ninsky.cronos.domain.port.ErrorCatalogEntry;
import com.ninsky.cronos.domain.port.ErrorCatalogPort;
import com.ninsky.cronos.infrastructure.web.RequestLocaleResolver;
import com.ninsky.cronos.infrastructure.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Runs inside the Spring Security filter chain, ahead of DispatcherServlet — so it cannot rely on
 * {@link GlobalExceptionHandler} or MVC's locale resolution and instead resolves locale straight
 * from the request header and writes the envelope directly, keeping unauthenticated responses
 * bilingual and shaped identically to every other error response.
 */
@Component
@RequiredArgsConstructor
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String ERROR_CODE = "AUTHENTICATION_FAILED";

    private final ErrorCatalogPort errorCatalogPort;
    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        Locale locale = RequestLocaleResolver.resolve(request);
        String traceId = MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY);

        var catalogEntry = errorCatalogPort.findByCode(ERROR_CODE);
        HttpStatus status = catalogEntry.map(e -> HttpStatus.valueOf(e.httpStatus())).orElse(HttpStatus.UNAUTHORIZED);
        String message = catalogEntry.map(e -> e.title(locale)).orElse(ERROR_CODE);
        List<ApiError> errors = catalogEntry.map(e -> detailError(e, locale)).orElse(List.of());

        ApiResponseEnvelope<Void> envelope = ApiResponseEnvelope.error(traceId, message, errors);

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(envelope));
    }

    private List<ApiError> detailError(ErrorCatalogEntry entry, Locale locale) {
        return List.of(new ApiError(entry.errorCode(), entry.description(locale), null, entry.imageUrl()));
    }
}
