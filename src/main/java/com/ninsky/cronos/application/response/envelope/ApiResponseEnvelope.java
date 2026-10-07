package com.ninsky.cronos.application.response.envelope;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Uniform outer shape for every HTTP response (success and error alike). Success responses are
 * wrapped into this via {@link com.ninsky.cronos.infrastructure.web.EnvelopeResponseBodyAdvice};
 * error responses are built directly by the global exception handling layer.
 * <p>
 * {@code data} is always serialized, even when null: "no resource" is a meaningful success answer
 * (e.g. {@code GET /users/me/fiscal} before registration, {@code DELETE /users/me/avatar}) and the
 * frontend distinguishes {@code data: null} from a missing key.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponseEnvelope<T>(
        Meta meta,
        String status,
        String message,
        @JsonInclude(JsonInclude.Include.ALWAYS) T data,
        List<ApiError> errors,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<ApiWarning> warnings
) {
    public record Meta(String traceId, Instant timestamp) {
        public static Meta now(String traceId) {
            return new Meta(traceId, Instant.now());
        }
    }

    public static <T> ApiResponseEnvelope<T> success(String traceId, String message, T data) {
        return success(traceId, message, data, null);
    }

    /** Success with non-blocking {@code warnings[]}; omitted from the JSON when empty. */
    public static <T> ApiResponseEnvelope<T> success(String traceId, String message, T data, List<ApiWarning> warnings) {
        return new ApiResponseEnvelope<>(Meta.now(traceId), "SUCCESS", message, data, null, warnings);
    }

    public static <T> ApiResponseEnvelope<T> error(String traceId, String message, List<ApiError> errors) {
        return new ApiResponseEnvelope<>(Meta.now(traceId), "ERROR", message, null, errors, null);
    }
}
