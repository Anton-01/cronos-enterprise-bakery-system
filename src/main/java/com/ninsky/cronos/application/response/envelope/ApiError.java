package com.ninsky.cronos.application.response.envelope;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * A single structured error item inside {@link ApiResponseEnvelope#errors()}.
 * {@code field} is the request-body JSON path for field-level errors ({@code address.zipCode}) and is
 * always serialized — explicitly {@code null} for errors not tied to one input.
 * {@code imageUrl} is populated for catalog-backed errors that carry one (e.g. the 404 route-not-found entry).
 * {@code details} carries structured context for the client (e.g. the recipes behind a RESOURCE_IN_USE); omitted when empty.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String code, String message, @JsonInclude(JsonInclude.Include.ALWAYS) String field, String imageUrl,
                       Map<String, Object> details) {

    public ApiError(String code, String message, String field, String imageUrl) {
        this(code, message, field, imageUrl, null);
    }

    public ApiError(String code, String message, String field) {
        this(code, message, field, null);
    }

    public ApiError(String code, String message) {
        this(code, message, null, null);
    }
}
