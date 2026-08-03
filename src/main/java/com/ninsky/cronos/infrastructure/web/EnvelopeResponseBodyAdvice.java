package com.ninsky.cronos.infrastructure.web;

import com.ninsky.cronos.application.response.core.ApiResponse;
import com.ninsky.cronos.application.response.envelope.ApiResponseEnvelope;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Wraps every existing {@link ApiResponse} controller return value into the uniform
 * {@link ApiResponseEnvelope} at serialization time, so the ~19 existing controllers don't need
 * touching and the {@code data} payload each already returns is preserved byte-for-byte inside
 * the envelope's {@code data} field.
 */
@RestControllerAdvice
public class EnvelopeResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                   Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                   ServerHttpRequest request, ServerHttpResponse response) {
        if (!(body instanceof ApiResponse<?> apiResponse)) {
            return body;
        }
        String traceId = MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY);
        return apiResponse.isSuccess()
                ? ApiResponseEnvelope.success(traceId, apiResponse.getMessage(), apiResponse.getData())
                : ApiResponseEnvelope.error(traceId, apiResponse.getMessage(), null);
    }
}
