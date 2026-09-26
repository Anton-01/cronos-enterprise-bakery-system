package com.ninsky.cronos.application.response.envelope;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A single structured error item inside {@link ApiResponseEnvelope#errors()}.
 * {@code field} is the request-body JSON path for field-level errors ({@code address.zipCode}) and is
 * always serialized — explicitly {@code null} for errors not tied to one input.
 * {@code imageUrl} is populated for catalog-backed errors that carry one (e.g. the 404 route-not-found entry).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String code, String message, @JsonInclude(JsonInclude.Include.ALWAYS) String field, String imageUrl) {

    public ApiError(String code, String message, String field) {
        this(code, message, field, null);
    }

    public ApiError(String code, String message) {
        this(code, message, null, null);
    }
}
